package web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class CaseController {

    private static final Path PROJECT_DIR  = Paths.get(System.getProperty("user.dir"));
    private static final Path CASES_FILE   = PROJECT_DIR.resolve("cases.json");
    private static final Path CONFIG_FILE  = PROJECT_DIR.resolve("web-config.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, JobInfo> jobs = new ConcurrentHashMap<>();

    // ── CASES ────────────────────────────────────────────────────────────────

    @GetMapping("/cases")
    public List<Map<String, Object>> getCases() throws IOException {
        if (!Files.exists(CASES_FILE)) return List.of();
        return MAPPER.readValue(CASES_FILE.toFile(), new TypeReference<>() {});
    }

    @PostMapping("/cases")
    public ResponseEntity<Void> addCase(@RequestBody Map<String, Object> c) throws IOException {
        List<Map<String, Object>> list = readCases();
        list.add(c);
        writeCases(list);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cases/{caseId}")
    public ResponseEntity<Void> updateCase(@PathVariable String caseId,
                                           @RequestBody Map<String, Object> updated) throws IOException {
        List<Map<String, Object>> list = readCases();
        for (int i = 0; i < list.size(); i++) {
            if (caseId.equals(list.get(i).get("caseId"))) {
                list.set(i, updated);
                writeCases(list);
                return ResponseEntity.ok().build();
            }
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/cases/{caseId}")
    public ResponseEntity<Void> deleteCase(@PathVariable String caseId) throws IOException {
        List<Map<String, Object>> list = readCases();
        boolean removed = list.removeIf(c -> caseId.equals(c.get("caseId")));
        if (removed) writeCases(list);
        return removed ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    // ── CONFIG ───────────────────────────────────────────────────────────────

    @GetMapping("/config")
    public Map<String, Object> getConfig() throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("baseUrl",    "https://mirketgateway.apps.erdek.paas.turktelekom.intra");
            def.put("token",      "");
            def.put("searchPath", "/search");
            def.put("limit",      20);
            return def;
        }
        return MAPPER.readValue(CONFIG_FILE.toFile(), new TypeReference<>() {});
    }

    @PostMapping("/config")
    public ResponseEntity<Void> saveConfig(@RequestBody Map<String, Object> config) throws IOException {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE.toFile(), config);
        return ResponseEntity.ok().build();
    }

    // ── RUN ──────────────────────────────────────────────────────────────────

    @PostMapping("/run")
    public Map<String, String> runTests() throws IOException {
        Map<String, Object> config = getConfig();
        String baseUrl    = String.valueOf(config.getOrDefault("baseUrl",    ""));
        String token      = String.valueOf(config.getOrDefault("token",      ""));
        String searchPath = String.valueOf(config.getOrDefault("searchPath", "/search"));
        int    limit      = Integer.parseInt(String.valueOf(config.getOrDefault("limit", 20)));

        String jobId = UUID.randomUUID().toString().substring(0, 8);
        JobInfo job  = new JobInfo(jobId);
        jobs.put(jobId, job);

        Thread t = new Thread(() -> {
            try {
                List<String> cmd = buildMvnCommand(baseUrl, token, searchPath, limit);
                job.log("Komut: " + String.join(" ", cmd));
                job.log("Çalışıyor...");

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(PROJECT_DIR.toFile());
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        job.log(line);
                    }
                }

                int exit = proc.waitFor();
                job.exitCode = exit;

                // Find the report file
                Optional<Path> report = Files.list(PROJECT_DIR)
                        .filter(p -> p.getFileName().toString().startsWith("TestReport_")
                                  && p.getFileName().toString().endsWith(".xlsx"))
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
                report.ifPresent(p -> job.reportFile = p);

                job.status = exit == 0 ? "DONE" : "DONE_WITH_ERRORS";
                job.log("Bitti. Çıkış kodu: " + exit);
            } catch (Exception e) {
                job.status = "ERROR";
                job.log("Hata: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();

        return Map.of("jobId", jobId);
    }

    @GetMapping("/jobs/{jobId}")
    public Map<String, Object> getJob(@PathVariable String jobId) {
        JobInfo job = jobs.get(jobId);
        if (job == null) return Map.of("error", "Job bulunamadı");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId",       job.jobId);
        result.put("status",      job.status);
        result.put("output",      job.output.toString());
        result.put("hasReport",   job.reportFile != null && Files.exists(job.reportFile));
        result.put("reportName",  job.reportFile != null ? job.reportFile.getFileName().toString() : "");
        return result;
    }

    @GetMapping("/jobs/{jobId}/report")
    public ResponseEntity<Resource> downloadReport(@PathVariable String jobId) {
        JobInfo job = jobs.get(jobId);
        if (job == null || job.reportFile == null) return ResponseEntity.notFound().build();

        Resource resource = new FileSystemResource(job.reportFile);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + job.reportFile.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private List<Map<String, Object>> readCases() throws IOException {
        if (!Files.exists(CASES_FILE)) return new ArrayList<>();
        return MAPPER.readValue(CASES_FILE.toFile(), new TypeReference<List<Map<String, Object>>>() {});
    }

    private void writeCases(List<Map<String, Object>> list) throws IOException {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(CASES_FILE.toFile(), list);
    }

    private List<String> buildMvnCommand(String baseUrl, String token, String searchPath, int limit) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> cmd = new ArrayList<>();
        if (isWindows) {
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add(findMvnCmd());
        } else {
            cmd.add("mvn");
        }
        cmd.add("test");
        cmd.add("-Dtest=MainCases");
        cmd.add("-DbaseUrl=" + baseUrl);
        cmd.add("-Dtoken=" + token);
        cmd.add("-DsearchPath=" + searchPath);
        cmd.add("-Dlimit=" + limit);
        cmd.add("-Dsurefire.failIfNoSpecifiedTests=false");
        return cmd;
    }

    /**
     * Windows'ta mvn.cmd'yi bul:
     * 1. IntelliJ'in javaagent argümanından kurulum dizinini çıkar → plugins/maven altındaki mvn.cmd
     * 2. Bulamazsa PATH'teki mvn.cmd'yi dene
     */
    private String findMvnCmd() {
        try {
            List<String> vmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : vmArgs) {
                if (arg.startsWith("-javaagent:") && arg.contains("idea_rt.jar")) {
                    String agentPath = arg.substring("-javaagent:".length());
                    int eq = agentPath.indexOf('=');
                    if (eq > 0) agentPath = agentPath.substring(0, eq);
                    Path ideaLib  = Paths.get(agentPath).toAbsolutePath().getParent(); // .../lib
                    Path ideaRoot = ideaLib != null ? ideaLib.getParent() : null;      // IntelliJ kök
                    if (ideaRoot != null) {
                        Path mvnCmd = ideaRoot.resolve("plugins/maven/lib/maven3/bin/mvn.cmd");
                        if (Files.exists(mvnCmd)) {
                            return mvnCmd.toAbsolutePath().toString();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "mvn.cmd"; // PATH'te varsa çalışır
    }

    // ── JOB INFO ─────────────────────────────────────────────────────────────

    static class JobInfo {
        final String jobId;
        volatile String status = "RUNNING";
        volatile int    exitCode = -1;
        volatile Path   reportFile;
        final StringBuilder output = new StringBuilder();

        JobInfo(String jobId) { this.jobId = jobId; }

        synchronized void log(String line) {
            output.append(line).append("\n");
        }
    }
}
