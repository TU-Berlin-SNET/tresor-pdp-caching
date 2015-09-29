package ilhn.xacml.benchmark.generator;

import ilhn.xacml.benchmark.BenchmarkUtil;
import org.wso2.balana.attr.IntegerAttribute;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class Generator {

    String policyTemplate;
    String ruleTemplate;
    String matchActionTemplate;
    String matchOrganizationTemplate;
    String matchPathTemplate;
    String requestTemplate;

    String combiningAlgId = "urn:oasis:names:tc:xacml:3.0:rule-combining-algorithm:deny-unless-permit";

    public Generator() throws FileNotFoundException {
        loadTemplates();
    }

    private void loadTemplates() throws FileNotFoundException {
        String basePath = String.join(File.separator, BenchmarkUtil.RESOURCE_PATH, "templates") + File.separator;

        policyTemplate = readTemplate(basePath, "PolicyTemplate.xml");
        ruleTemplate = readTemplate(basePath, "RuleTemplate.xml");
        matchActionTemplate = readTemplate(basePath, "MatchAction.xml");
        matchOrganizationTemplate = readTemplate(basePath, "MatchOrganization.xml");
        matchPathTemplate = readTemplate(basePath, "MatchPath.xml");
        requestTemplate = readTemplate(basePath, "RequestTemplate.xml");
    }

    private String readTemplate(String basePath, String fileName) throws FileNotFoundException {
        try (Scanner scanner = new Scanner(new File(basePath + fileName)).useDelimiter("\\A")) {
            return scanner.next();
        }
    }

    public void write100PoliciesWith1kRules(String path) throws IOException {
        String p = (path.endsWith(File.separator)) ? path : path + File.separator;

        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                String fileName = createFileName(Integer.parseInt(x + "" + y));
                String policyId = fileName;
                String policyMatches = matchPath(x) + matchAction(y);

                StringBuilder builder = new StringBuilder();
                for (int n = 0; n < 1000; n++)
                    builder.append(rule(n, matchOrganization(n)));

                String policy = policy(policyId, policyMatches, combiningAlgId, builder.toString());
                Files.write(Paths.get(p, fileName), policy.getBytes());
            }
        }
    }

    public void write1kPoliciesWith100Rules(String path) throws IOException {
        String p = (path.endsWith(File.separator)) ? path : path + File.separator;

        for (int n = 0; n < 1000; n++) {
            String filename = createFileName(n);
            String policyId = filename;
            String policyMatches = matchOrganization(n);

            StringBuilder builder = new StringBuilder();
            for (int x = 0; x < 10; x++)
                for (int y = 0; y < 10; y++)
                    builder.append(rule(Integer.parseInt(x+""+n), matchPath(x)+matchAction(y)));

            String policy = policy(policyId, policyMatches, combiningAlgId, builder.toString());
            Files.write(Paths.get(p, filename), policy.getBytes());
        }
    }

    public void write10kPoliciesWith10Rules(String path) throws IOException {
        String p = (path.endsWith(File.separator)) ? path : path + File.separator;

        for (int n = 0; n < 1000; n++) {
            for (int x = 0; x < 10; x++) {
                String filename = createFileName(Integer.parseInt(n + "" + x));
                String policyId = filename;
                String policyMatches = matchPath(x) + matchOrganization(n);

                StringBuilder builder = new StringBuilder();
                for (int y = 0; y < 10; y++)
                    builder.append(rule(y, matchAction(y)));

                String policy = policy(policyId, policyMatches, combiningAlgId, builder.toString());
                Files.write(Paths.get(p, filename), policy.getBytes());
            }
        }
    }

    public void copy1000PoliciesWith10Rules(String from, String to) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(from))) {
            int n = 0;
            for (Path path : stream) {
                Files.copy(path, Paths.get(to, path.getFileName().toString()));

                ++n;
                if (n == 1000)
                    break;
            }
        }
    }

    public void write100kRequests(String dirpath) throws IOException {
        Random random = new Random();
        String p = (dirpath.endsWith(File.separator)) ? dirpath : dirpath + File.separator;
        String[] roleId = new String[]{ "chief", "employee", "authorized", "unauthorized", "intern", "temp", "associate", "unassociated", "trusted", "untrusted" };

        for (int n = 0; n < 1000; n++) {
            for (int x = 0; x < 10; x++) {
                for (int y = 0; y < 10; y++) {
                    String number = String.format("%04d", n) + String.format("%02d", x) + String.format("%02d", y);
                    String filename = "Request" + number + ".xml";
                    String subjectId = "user" + number;
                    String organizationId = "organization"+n;
                    String role = roleId[random.nextInt(roleId.length)];
                    String path = "/path"+x;
                    String action = "action"+y;

                    String request = requestTemplate
                            .replace("{subjectId}", subjectId)
                            .replace("{organizationId}", organizationId)
                            .replace("{roleId}", role)
                            .replace("{path}", path)
                            .replace("{action}", action);

                    Files.write(Paths.get(p, filename), request.getBytes());
                }
            }
        }
    }

    private String policy(String policyId, String policyMatches, String combiningAlgId, String rules) {
        return policyTemplate.replace("{policyId}", policyId)
                .replace("{combiningAlgId}", combiningAlgId)
                .replace("{policy-matches}", policyMatches)
                .replace("{rules}", rules);
    }

    private String rule(int n, String s) {
        return ruleTemplate.replace("{ruleId}", "Rule"+n).replace("{rule-matches}", s);
    }

    private String matchOrganization(int n) {
        return matchOrganizationTemplate.replace("{organization}", "organization"+n);
    }

    private String matchAction(int y) {
        return matchActionTemplate.replace("{action}", "action"+y);
    }

    private String matchPath(int x) {
        return matchPathTemplate.replace("{path}", "path"+x);
    }

    private String createFileName(int i) {
        return "Policy" + String.format("%05d", i) + ".xml";
    }

    public static void main(String[] args) throws Exception {
        Generator generator = new Generator();

        Files.createDirectories(Paths.get(BenchmarkUtil.POLICIES_100));
        Files.createDirectories(Paths.get(BenchmarkUtil.POLICIES_1k));
        Files.createDirectories(Paths.get(BenchmarkUtil.POLICIES_10k));
        Files.createDirectories(Paths.get(BenchmarkUtil.POLICIES_1k10));
        Files.createDirectories(Paths.get(BenchmarkUtil.REQUESTS));

        generator.write100PoliciesWith1kRules(BenchmarkUtil.POLICIES_100);
        generator.write1kPoliciesWith100Rules(BenchmarkUtil.POLICIES_1k);
        generator.write10kPoliciesWith10Rules(BenchmarkUtil.POLICIES_10k);
        generator.copy1000PoliciesWith10Rules(BenchmarkUtil.POLICIES_10k, BenchmarkUtil.POLICIES_1k10);
        generator.write100kRequests(BenchmarkUtil.REQUESTS);

        System.out.println("Finished");
    }

}
