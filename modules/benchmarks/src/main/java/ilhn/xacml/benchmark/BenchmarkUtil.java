package ilhn.xacml.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class BenchmarkUtil {
    private static Logger log = LoggerFactory.getLogger(BenchmarkUtil.class);
    public static final String MODULE_PATH = String.join(File.separator, "modules", "benchmarks");
    public static final String RESOURCE_SUB_PATH = String.join(File.separator, "src", "main", "resources");

    public static final String BASE_PATH;
    public static final String RESOURCE_PATH;
    public static final String POLICIES_100;
    public static final String POLICIES_1k;
    public static final String POLICIES_1k10;
    public static final String POLICIES_10k;
    public static final String POLICIES_3k_MIX;
    public static final String REQUESTS;
    static {
        try {
            BASE_PATH = new File(".").getCanonicalPath();
            RESOURCE_PATH = String.join(File.separator, BASE_PATH, MODULE_PATH, RESOURCE_SUB_PATH);
            POLICIES_100 = String.join(File.separator, BASE_PATH, "policies", "100_policies_1000_rules");
            POLICIES_1k = String.join(File.separator, BASE_PATH, "policies", "1000_policies_100_rules");
            POLICIES_1k10 = String.join(File.separator, BASE_PATH, "policies", "1000_policies_10_rules");
            POLICIES_10k = String.join(File.separator, BASE_PATH, "policies", "10000_policies_10_rules");
            POLICIES_3k_MIX = String.join(File.separator, BASE_PATH, "policies", "3000_policies_mix_rules");
            REQUESTS = String.join(File.separator, BASE_PATH, "requests");

        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static List<String> loadStrings(String dirPath) throws FileNotFoundException {
        File dir = new File(dirPath);
        return loadStrings(Arrays.asList(dir.listFiles()));
    }

    public static List<String> loadStrings(String dirPath, int i) throws FileNotFoundException {
        File dir = new File(dirPath);
        List<File> files = Arrays.asList(dir.listFiles());
        if (files.size() > i)
            files = files.subList(0, i);

        return loadStrings(files);
    }

    public static List<String> loadStrings(List<File> files) throws FileNotFoundException {
        List<String> list = new ArrayList<>(files.size());
        for (File file : files) {
            try (Scanner scanner = new Scanner(file).useDelimiter("\\A")) {
                list.add(scanner.next());
            }
        }
        return list;
    }

}
