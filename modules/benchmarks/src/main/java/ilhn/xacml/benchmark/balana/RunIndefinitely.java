package ilhn.xacml.benchmark.balana;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ilhn.xacml.benchmark.BenchmarkUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.PDP;
import org.wso2.balana.PDPConfig;
import org.wso2.balana.finder.*;
import org.wso2.balana.finder.impl.CurrentEnvModule;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;
import org.wso2.balana.finder.impl.SelectorModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Class used for analysis in bachelor thesis "Caching and performance optimization in XACML".
 * Used in conjunction with VisualVM.
 * Keeps running requests for evaluation to a balana PDP.
 */
public class RunIndefinitely {
    private static Logger log = LoggerFactory.getLogger(RunIndefinitely.class);

    public static void main(String[] args) throws IOException {
        AttributeFinder attributeFinder = new AttributeFinder();
        List<AttributeFinderModule> attributeFinderModules = new LinkedList<>();
        attributeFinderModules.add(new CurrentEnvModule());
        attributeFinderModules.add(new SelectorModule());
        attributeFinder.setModules(attributeFinderModules);
        PolicyFinder policyFinder = new PolicyFinder();
        Set<PolicyFinderModule> policyFinderModules = new HashSet<>(1);
        policyFinderModules.add(new FileBasedPolicyFinderModule(BenchmarkUtil.POLICIES_1k));
        policyFinder.setModules(policyFinderModules);
        ResourceFinder resourceFinder = new ResourceFinder();
        resourceFinder.setModules(new LinkedList<>());

        PDP pdp = new PDP(new PDPConfig(attributeFinder, policyFinder, resourceFinder));

        Random random = new Random();
        List<String> requests = BenchmarkUtil.loadStrings(BenchmarkUtil.REQUESTS, 10000);

        Cache<String, String> cache = Caffeine.newBuilder().maximumSize(10).build();
        log.info("Finished setup, begin running...");

        boolean run = true;
        while (run) {
            String response = pdp.evaluate( requests.get(random.nextInt(requests.size())) );
            cache.put(response, response);
        }
    }

}
