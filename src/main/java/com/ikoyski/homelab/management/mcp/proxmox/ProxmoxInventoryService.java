package com.ikoyski.homelab.management.mcp.proxmox;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Read-only Proxmox VE inventory adapter. Only allowlisted fields are returned. */
@Service
public class ProxmoxInventoryService {
    private static final Logger log = LoggerFactory.getLogger(ProxmoxInventoryService.class);
    private static final List<String> NODE_FIELDS = List.of("node","status","level","cpu","maxcpu","mem","maxmem","disk","maxdisk","uptime");
    private static final List<String> GUEST_FIELDS = List.of("vmid","node","type","name","status","cpu","maxcpu","mem","maxmem","disk","maxdisk","uptime");
    private static final List<String> STORAGE_FIELDS = List.of("storage","node","type","content","active","enabled","shared","used","avail","total");
    private final Environment environment;
    private final RestClient client;

    public ProxmoxInventoryService(Environment environment, RestClient.Builder builder) {
        this.environment = environment;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(environment.getProperty("homelab.proxmox.connect-timeout-ms", Integer.class, 3000)));
        factory.setReadTimeout(Duration.ofMillis(environment.getProperty("homelab.proxmox.read-timeout-ms", Integer.class, 8000)));
        this.client = builder.baseUrl(environment.getProperty("homelab.proxmox.api-url", "https://invalid.invalid"))
                .requestFactory(factory).defaultHeader("Authorization", authHeader(environment)).build();
    }

    public Map<String,Object> nodes() { return collect("/api2/json/nodes", NODE_FIELDS, null); }

    public Map<String,Object> guests() {
        Map<String,Object> result = collect("/api2/json/cluster/resources?type=vm", GUEST_FIELDS,
                item -> "lxc".equals(item.get("type")) || "qemu".equals(item.get("type")));
        Object value = result.get("items");
        if ("ok".equals(result.get("status")) && value instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?,?> guest && "qemu".equals(guest.get("type"))) {
                    @SuppressWarnings("unchecked")
                    Map<String,Object> qemuGuest = (Map<String,Object>) guest;
                    addFilesystemInfo(qemuGuest);
                }
            }
        }
        // Include QEMU filesystem enrichment in this section's collection duration.
        result.put("collectedAt", collectedAt());
        return result;
    }

    public Map<String,Object> storage() {
        Map<String,Object> result = collect("/api2/json/cluster/resources?type=storage", STORAGE_FIELDS, null);
        Object value = result.get("items");
        if (!"ok".equals(result.get("status")) || !(value instanceof List<?> items)) {
            return sortStorageEntries(result);
        }

        List<String> errors = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?,?> entry) {
                @SuppressWarnings("unchecked")
                Map<String,Object> storage = (Map<String,Object>) entry;
                enrichStorageStatus(storage, errors);
            }
        }
        if (!errors.isEmpty()) {
            result.put("status", "partial");
            result.put("errors", errors);
        }
        return sortStorageEntries(result);
    }

    private void enrichStorageStatus(Map<String,Object> storage, List<String> errors) {
        Object storageName = storage.get("storage");
        Object nodeName = storage.get("node");
        if (!(storageName instanceof String name) || name.isBlank()
                || !(nodeName instanceof String node) || node.isBlank()) {
            String errorCode = "storage_identity_unavailable";
            storage.put("storageStatus", "unavailable");
            storage.put("storageError", errorCode);
            errors.add(String.valueOf(storageName) + ": " + errorCode);
            return;
        }

        try {
            JsonNode response = client.get()
                    .uri("/api2/json/nodes/{node}/storage/{storage}/status", node, name)
                    .retrieve().body(JsonNode.class);
            JsonNode data = response == null ? null : response.get("data");
            storage.putAll(safeStorageStatusFields(data));
            storage.put("storageStatus", "ok");
        } catch (Exception ex) {
            String errorCode = storageFailureCode(ex);
            Integer httpStatus = ex instanceof org.springframework.web.client.RestClientResponseException responseException
                    ? responseException.getStatusCode().value() : null;
            log.warn("Proxmox storage status request failed node={} storage={} errorCode={} httpStatus={}",
                    node, name, errorCode, httpStatus);
            storage.put("storageStatus", "unavailable");
            storage.put("storageError", errorCode);
            errors.add(name + ": " + errorCode);
        }
    }

    static Map<String,Object> safeStorageStatusFields(JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new IllegalStateException("Unexpected Proxmox storage status response shape");
        }

        Map<String,Object> result = new LinkedHashMap<>();
        copyTextField(data, result, "type", "storageType");
        copyNumberField(data, result, "total", "totalBytes");
        copyNumberField(data, result, "used", "usedBytes");
        copyNumberField(data, result, "avail", "availableBytes");
        copyScalarField(data, result, "active");
        copyScalarField(data, result, "enabled");
        copyScalarField(data, result, "shared");

        Object total = result.get("totalBytes");
        Object used = result.get("usedBytes");
        if (total instanceof Number totalBytes && used instanceof Number usedBytes
                && totalBytes.longValue() > 0 && usedBytes.longValue() >= 0) {
            result.put("usagePercent",
                    Math.round(usedBytes.doubleValue() * 1000.0 / totalBytes.doubleValue()) / 10.0);
        }
        return result;
    }

    static String storageFailureCode(Exception ex) {
        if (ex instanceof IllegalStateException stateException
                && "Unexpected Proxmox storage status response shape".equals(stateException.getMessage())) {
            return "unexpected_response";
        }
        if (ex instanceof org.springframework.web.client.RestClientResponseException) {
            return "storage_status_http_error";
        }
        return "storage_status_request_failed";
    }

    public Map<String,Object> inventory() {
        Map<String,Object> n=nodes(), g=guests(), s=storage();
        List<String> errors=new ArrayList<>();
        addError(errors,"nodes",n); addError(errors,"guests",g); addError(errors,"storage",s);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("status",errors.isEmpty()?"complete":"partial");
        result.put("nodes",n.getOrDefault("items",List.of()));
        result.put("guests",g.getOrDefault("items",List.of()));
        result.put("storage",s.getOrDefault("items",List.of()));
        result.put("errors",errors);
        // This marks completion of the combined collection, not a shared snapshot time.
        result.put("collectedAt",collectedAt());
        return result;
    }

    private void addFilesystemInfo(Map<String,Object> guest) {
        String node = String.valueOf(guest.get("node"));
        Object vmid = guest.get("vmid");
        List<Map<String,Object>> filesystems = new ArrayList<>();
        try {
            if (!(vmid instanceof Number)) {
                throw new IllegalStateException("QEMU guest ID is unavailable");
            }
            String path = "/api2/json/nodes/{node}/qemu/{vmid}/agent/get-fsinfo";
            JsonNode response = client.get().uri(path, node, vmid).retrieve().body(JsonNode.class);
            filesystems.addAll(filesystemEntries(response));
            filesystems.sort(Comparator
                    .comparing((Map<String,Object> fs) -> String.valueOf(fs.getOrDefault("mountpoint", "")))
                    .thenComparing(fs -> String.valueOf(fs.getOrDefault("name", ""))));
            guest.put("filesystemStatus", "ok");
            guest.put("filesystems", filesystems);
        } catch (Exception ex) {
            String errorCode = filesystemFailureCode(ex);
            Integer httpStatus = ex instanceof org.springframework.web.client.RestClientResponseException responseException
                    ? responseException.getStatusCode().value() : null;
            log.warn("Proxmox guest filesystem request failed node={} vmid={} errorCode={} httpStatus={}",
                    node, vmid, errorCode, httpStatus);
            guest.put("filesystemStatus", "unavailable");
            guest.put("filesystems", List.of());
            guest.put("filesystemError", errorCode);
        }
    }

    static List<Map<String,Object>> filesystemEntries(JsonNode response) {
        JsonNode data = response == null ? null : response.get("data");
        JsonNode result = data == null ? null : data.get("result");
        if (result == null || !result.isArray()) {
            throw new IllegalStateException("Unexpected QEMU guest-agent response shape");
        }

        List<Map<String,Object>> filesystems = new ArrayList<>();
        for (JsonNode entry : result) {
            filesystems.add(safeFilesystemFields(entry));
        }
        return filesystems;
    }

    static String filesystemFailureCode(Exception ex) {
        if (ex instanceof IllegalStateException stateException) {
            if ("QEMU guest ID is unavailable".equals(stateException.getMessage())) {
                return "guest_id_unavailable";
            }
            if ("Unexpected QEMU guest-agent response shape".equals(stateException.getMessage())) {
                return "unexpected_response";
            }
        }
        if (ex instanceof org.springframework.web.client.RestClientResponseException) {
            return "guest_agent_http_error";
        }
        return "guest_filesystem_request_failed";
    }

    private Map<String,Object> collect(String path,List<String> fields,Predicate<Map<String,Object>> filter) {
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            requireConfiguration();
            JsonNode response=client.get().uri(path).retrieve().body(JsonNode.class);
            JsonNode data=response==null?null:response.get("data");
            if(data==null||!data.isArray()) throw new IllegalStateException("Unexpected Proxmox API response shape");
            List<Map<String,Object>> items=new ArrayList<>();
            for(JsonNode entry:data) {
                Map<String,Object> item=safeFields(entry,fields);
                if(filter==null||filter.test(item)) items.add(item);
            }
            result.put("status","ok"); result.put("count",items.size()); result.put("items",items);
        } catch(Exception ex) {
            log.warn("Proxmox inventory request failed path={} cause={}",path,ex.getClass().getSimpleName());
            result.put("status","error"); result.put("count",0); result.put("items",List.of());
            result.put("error","Proxmox request failed ("+ex.getClass().getSimpleName()+")");
        }
        result.put("collectedAt",collectedAt());
        return result;
    }
    private void requireConfiguration() {
        for(String key:List.of("homelab.proxmox.api-url","homelab.proxmox.token-id","homelab.proxmox.token-secret")) {
            String value=environment.getProperty(key);
            if(value==null||value.isBlank()) throw new IllegalStateException("Proxmox configuration is incomplete");
        }
        URI apiUri=URI.create(environment.getProperty("homelab.proxmox.api-url"));
        if(!"https".equalsIgnoreCase(apiUri.getScheme())||apiUri.getHost()==null) {
            throw new IllegalStateException("Proxmox API URL must use HTTPS and include a host");
        }
    }
    private static String authHeader(Environment env) {
        return "PVEAPIToken="+env.getProperty("homelab.proxmox.token-id","")+"="+env.getProperty("homelab.proxmox.token-secret","");
    }
    private static Map<String,Object> safeFields(JsonNode node,List<String> fields) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String field:fields) {
            JsonNode value=node.get(field);
            if(value!=null&&!value.isNull()) {
                Object safeValue=value.isNumber()?value.numberValue():value.isBoolean()?value.booleanValue():value.asText();
                if("content".equals(field) && safeValue instanceof String content) {
                    safeValue=sortContentTypes(content);
                }
                result.put(field,safeValue);
            }
        }
        return result;
    }

    static Map<String,Object> safeFilesystemFields(JsonNode node) {
        Map<String,Object> result = new LinkedHashMap<>();
        copyTextField(node, result, "name");
        copyTextField(node, result, "mountpoint");
        copyTextField(node, result, "type");

        JsonNode totalNode = node.get("total-bytes");
        JsonNode usedNode = node.get("used-bytes");
        if (totalNode != null && totalNode.isNumber() && usedNode != null && usedNode.isNumber()) {
            long totalBytes = Math.max(0L, totalNode.longValue());
            long usedBytes = Math.max(0L, usedNode.longValue());
            result.put("totalBytes", totalBytes);
            result.put("usedBytes", usedBytes);
            result.put("availableBytes", Math.max(0L, totalBytes - usedBytes));
            if (totalBytes > 0) {
                result.put("usagePercent", Math.round(usedBytes * 1000.0 / totalBytes) / 10.0);
            }
        }
        return result;
    }

    private static void copyTextField(JsonNode source, Map<String,Object> target, String field) {
        copyTextField(source, target, field, field);
    }

    private static void copyTextField(JsonNode source, Map<String,Object> target, String sourceField,
            String targetField) {
        JsonNode value = source.get(sourceField);
        if (value != null && !value.isNull() && value.isValueNode()) {
            target.put(targetField, value.asText());
        }
    }

    private static void copyNumberField(JsonNode source, Map<String,Object> target, String sourceField,
            String targetField) {
        JsonNode value = source.get(sourceField);
        if (value != null && value.isNumber()) {
            target.put(targetField, value.longValue());
        }
    }

    private static void copyScalarField(JsonNode source, Map<String,Object> target, String field) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull() && (value.isNumber() || value.isBoolean())) {
            target.put(field, value.isNumber() ? value.numberValue() : value.booleanValue());
        }
    }
  
    static String collectedAt() {
        return Instant.now().toString();
    }

    static String sortContentTypes(String content) {
        return Arrays.stream(content.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    static Map<String,Object> sortStorageEntries(Map<String,Object> result) {
        Object value=result.get("items");
        if (!(value instanceof List<?> items)) {
            return result;
        }

        List<?> sortedItems=items.stream()
                .sorted(Comparator.comparing(item -> {
                    if (item instanceof Map<?,?> storage && storage.get("storage") != null) {
                        return storage.get("storage").toString();
                    }
                    return "";
                }))
                .toList();
        result.put("items",sortedItems);
        return result;
    }

    private static void addError(List<String> errors,String name,Map<String,Object> section) {
        if ("partial".equals(section.get("status"))) {
            Object sectionErrors = section.get("errors");
            if (sectionErrors instanceof List<?> details) {
                for (Object detail : details) {
                    errors.add(name + ": " + detail);
                }
            } else {
                errors.add(name + ": partial");
            }
        } else if (!"ok".equals(section.get("status"))) {
            errors.add(name + ": " + section.getOrDefault("error","unavailable"));
        }
    }
}
