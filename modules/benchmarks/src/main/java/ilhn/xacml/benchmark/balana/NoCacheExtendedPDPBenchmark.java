package ilhn.xacml.benchmark.balana;

import ilhn.xacml.ExtendedPDP;
import ilhn.xacml.benchmark.BenchmarkUtil;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.PDPConfig;
import org.wso2.balana.finder.*;
import org.wso2.balana.finder.impl.CurrentEnvModule;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;
import org.wso2.balana.finder.impl.SelectorModule;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark for the general performance of the Extended PDP
 * without caches, utilizing policy finder module type 1,
 * the FilebasedPolicyFinderModule from balana.
 */
@State(Scope.Benchmark)
public class NoCacheExtendedPDPBenchmark {
    private static Logger log = LoggerFactory.getLogger(NoCacheExtendedPDPBenchmark.class);

    Random random;
    List<String> requests;
    AtomicInteger index;

    @Param({ "100", "1000", "1010", "10000" })
    int policyCount;

    @Param({ "0", "1000" })
    int decisionCacheSize;

    ExtendedPDP pdp;

    @Setup
    public void setup() throws FileNotFoundException {
        log.info("Setting up");
        String policyLocation = "";
        switch (policyCount) {
            case 100: policyLocation = BenchmarkUtil.POLICIES_100; break;
            case 1000: policyLocation = BenchmarkUtil.POLICIES_1k; break;
            case 1010: policyLocation = BenchmarkUtil.POLICIES_1k10; break;
            case 10000: policyLocation = BenchmarkUtil.POLICIES_10k; break;
        }
        log.info("PolicyLocation is {}", policyLocation);

        PolicyFinderModule pfm = new FileBasedPolicyFinderModule(policyLocation);
        log.info("PolicyFinderModule is {}", pfm.getClass().getSimpleName());

        log.info("Loading requests");
        requests = BenchmarkUtil.loadStrings(BenchmarkUtil.REQUESTS, 10000);
        log.info("Loaded {} requests", requests.size());
        random = new Random();

        AttributeFinder attributeFinder = new AttributeFinder();
        List<AttributeFinderModule> attributeFinderModules = new ArrayList<>(5);
        attributeFinderModules.add(new CurrentEnvModule());
        attributeFinderModules.add(new SelectorModule());
        attributeFinder.setModules(attributeFinderModules);
        PolicyFinder policyFinder = new PolicyFinder();
        Set<PolicyFinderModule> policyFinderModules = new HashSet<>(1);
        policyFinderModules.add(pfm);
        policyFinder.setModules(policyFinderModules);
        ResourceFinder resourceFinder = new ResourceFinder();
        resourceFinder.setModules(new ArrayList<>(0));

        log.info("Starting PDP...");
        pdp = new ExtendedPDP(new PDPConfig(attributeFinder, policyFinder, resourceFinder), decisionCacheSize);
        log.info("PDP started.");
        log.info("Ready to go...");
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        index = new AtomicInteger(0);
    }

    @Benchmark
    public String processRandomRequest() throws InterruptedException {
        String request = requests.get(random.nextInt(requests.size()));
        return pdp.evaluate(request);
    }

    @Benchmark
    public String processSuccessiveRequest() throws InterruptedException {
        String request = requests.get(index.getAndIncrement() % requests.size());
        return pdp.evaluate(request);
    }

}
