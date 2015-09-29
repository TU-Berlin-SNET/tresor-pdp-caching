package ilhn.xacml.benchmark.balana;

import ilhn.xacml.ExtendedPDP;
import ilhn.xacml.benchmark.BenchmarkUtil;
import ilhn.xacml.finder.ProxyFilebasedPolicyFinderModule;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.PDPConfig;
import org.wso2.balana.finder.*;
import org.wso2.balana.finder.impl.CurrentEnvModule;
import org.wso2.balana.finder.impl.SelectorModule;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark for the general performance of the Extended PDP
 * under different caching conditions and configurations.
 * Utilizes only policy finder module type 2, the
 * ProxyFilebasedPolicyFinderModule from ExtendedPDP
 */
@State(Scope.Benchmark)
public class CacheExtendedPDPBenchmark {
    private static Logger log = LoggerFactory.getLogger(CacheExtendedPDPBenchmark.class);

    Random random;
    List<String> requests;
    AtomicInteger index;

    @Param({ "2" })
    int policyFinderType;

    @Param({ "10000" })
    int requestCount;

    @Param({ "100", "1000", "1010", "10000" })
    int policyCount;

    @Param({ "0.25", "0.5" })
    float finderResultCacheFraction;

    @Param({ "0.25", "0.5", "1" })
    float finderPolicyCacheFraction;

    @Param({ "0" , "1000" })
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
        int finderResultCache = Math.round(finderResultCacheFraction * requestCount);
        int finderPolicyCache = Math.round(finderPolicyCacheFraction * policyCount);
        log.info("PolicyFinder result cache size is {}, policy cache size is {}", finderResultCache, finderPolicyCache);

        PolicyFinderModule pfm = new ProxyFilebasedPolicyFinderModule(policyLocation, finderResultCache, finderPolicyCache);
        log.info("PolicyFinderModule is {}", pfm.getClass().getSimpleName());

        log.info("Loading {} requests", requestCount);
        requests = BenchmarkUtil.loadStrings(BenchmarkUtil.REQUESTS, requestCount);
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
