package com.adlin.orin.modules.monitor.service;

import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.*;

/**
 * 本地服务器信息采集服务
 * 使用 OSHI 库采集运行本应用的服务器的硬件信息
 */
@Slf4j
@Service
public class LocalServerInfoService {

    private SystemInfo systemInfo;

    @PostConstruct
    public void init() {
        systemInfo = new SystemInfo();
    }

    /**
     * 获取本地服务器硬件信息 (包含实时指标)
     */
    public Map<String, Object> getLocalServerInfo() {
        Map<String, Object> info = new HashMap<>();

        try {
            // 操作系统信息
            OperatingSystem os = systemInfo.getOperatingSystem();
            info.put("os", os.getFamily());
            info.put("osName", os.getFamily());

            // CPU 信息
            CentralProcessor processor = systemInfo.getHardware().getProcessor();
            info.put("cpuModel", processor.getProcessorIdentifier().getName());
            info.put("cpuCores", processor.getPhysicalProcessorCount());
            info.put("cpuLogicalCores", processor.getLogicalProcessorCount());

            // Load Average (实时系统负载)
            double[] loadAverage = processor.getSystemLoadAverage(3);
            info.put("loadAverage1m", loadAverage[0]);
            info.put("loadAverage5m", loadAverage[1]);
            info.put("loadAverage15m", loadAverage[2]);

            // CPU 使用率 (通过获取 CPU 空闲时间计算)
            long[] cpuLoad = processor.getSystemCpuLoadTicks();
            long idle = cpuLoad[3]; // IDLE is at index 3
            long total = Arrays.stream(cpuLoad).sum();
            double cpuUsage = total > 0 ? ((double) (total - idle) / total) * 100 : 0;
            info.put("cpuUsage", cpuUsage);

            // 内存信息
            GlobalMemory memory = systemInfo.getHardware().getMemory();
            long memoryTotal = memory.getTotal();
            long memoryAvailable = memory.getAvailable();
            long memoryUsed = memoryTotal - memoryAvailable;
            info.put("memoryTotal", memoryTotal);
            info.put("memoryAvailable", memoryAvailable);
            info.put("memoryUsed", memoryUsed);
            info.put("memoryUsagePercent", memoryTotal > 0 ? ((double) memoryUsed / memoryTotal) * 100 : 0);

            // 磁盘信息
            List<Map<String, Object>> disks = new ArrayList<>();
            for (HWDiskStore disk : systemInfo.getHardware().getDiskStores()) {
                Map<String, Object> diskInfo = new HashMap<>();
                diskInfo.put("name", disk.getName());
                diskInfo.put("size", disk.getSize());
                diskInfo.put("model", disk.getModel());
                disks.add(diskInfo);
            }
            info.put("disks", disks);

            // 汇总磁盘总大小
            long totalDisk = disks.stream()
                    .mapToLong(d -> (Long) d.getOrDefault("size", 0L))
                    .sum();
            info.put("diskTotal", totalDisk);

            // GPU 信息 (通过 GraphicsCard)
            List<Map<String, Object>> gpus = new ArrayList<>();
            for (GraphicsCard gpu : systemInfo.getHardware().getGraphicsCards()) {
                Map<String, Object> gpuInfo = new HashMap<>();
                gpuInfo.put("name", gpu.getName());
                gpuInfo.put("vendor", gpu.getVendor());
                gpus.add(gpuInfo);
            }
            info.put("gpus", gpus);

            // 取第一个 GPU 作为主 GPU
            if (!gpus.isEmpty()) {
                info.put("gpuModel", gpus.get(0).get("name"));
            }

            // 网络信息
            List<Map<String, Object>> networks = new ArrayList<>();
            for (NetworkIF net : systemInfo.getHardware().getNetworkIFs()) {
                Map<String, Object> netInfo = new HashMap<>();
                netInfo.put("name", net.getName());
                netInfo.put("displayName", net.getDisplayName());
                netInfo.put("macAddress", net.getMacaddr());
                netInfo.put("ipv4", Arrays.toString(net.getIPv4addr()));
                netInfo.put("speed", net.getSpeed());
                networks.add(netInfo);
            }
            info.put("networks", networks);

            // 计算机系统信息
            ComputerSystem cs = systemInfo.getHardware().getComputerSystem();
            info.put("manufacturer", cs.getManufacturer());
            info.put("model", cs.getModel());
            info.put("serialNumber", cs.getSerialNumber());

            // 系统正常运行时间
            info.put("uptime", os.getSystemUptime());
            info.put("processCount", os.getProcessCount());
            info.put("threadCount", os.getThreadCount());

            log.info("Collected local server info: CPU={}, Memory={}, OS={}, LoadAvg={}",
                    info.get("cpuModel"), FormatUtil.formatBytes((Long) info.get("memoryTotal")),
                    info.get("os"), loadAverage[0]);

        } catch (Exception e) {
            log.error("Failed to collect local server info", e);
            info.put("error", e.getMessage());
        }

        return info;
    }

    /**
     * 获取简单的服务器信息 (用于首次展示)
     */
    public Map<String, Object> getSimpleServerInfo() {
        Map<String, Object> info = getLocalServerInfo();
        Map<String, Object> simple = new HashMap<>();

        simple.put("os", info.getOrDefault("os", "Unknown"));
        simple.put("cpuModel", info.getOrDefault("cpuModel", "Unknown"));
        simple.put("cpuCores", info.getOrDefault("cpuCores", 0));
        simple.put("memoryTotal", info.getOrDefault("memoryTotal", 0L));
        simple.put("diskTotal", info.getOrDefault("diskTotal", 0L));
        simple.put("gpuModel", info.getOrDefault("gpuModel", "Unknown"));

        return simple;
    }

    /**
     * 获取当前系统负载信息
     */
    public Map<String, Object> getSystemLoad() {
        Map<String, Object> load = new HashMap<>();

        try {
            CentralProcessor processor = systemInfo.getHardware().getProcessor();
            double[] loadAverage = processor.getSystemLoadAverage(3);
            load.put("loadAverage1m", loadAverage[0]);
            load.put("loadAverage5m", loadAverage[1]);
            load.put("loadAverage15m", loadAverage[2]);

            GlobalMemory memory = systemInfo.getHardware().getMemory();
            load.put("memoryTotal", memory.getTotal());
            load.put("memoryAvailable", memory.getAvailable());
            load.put("memoryUsed", memory.getTotal() - memory.getAvailable());
            load.put("memoryUsagePercent", String.format("%.2f",
                    (double) (memory.getTotal() - memory.getAvailable()) / memory.getTotal() * 100));

            OperatingSystem os = systemInfo.getOperatingSystem();
            load.put("processCount", os.getProcessCount());
            load.put("threadCount", os.getThreadCount());

        } catch (Exception e) {
            log.error("Failed to get system load", e);
            load.put("error", e.getMessage());
        }

        return load;
    }
}
