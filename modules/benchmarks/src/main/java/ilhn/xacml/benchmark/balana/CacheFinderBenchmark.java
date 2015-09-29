package ilhn.xacml.benchmark.balana;

import ilhn.xacml.benchmark.BenchmarkUtil;
import ilhn.xacml.finder.ProxyFilebasedPolicyFinderModule;
import ilhn.xacml.util.XACML3StreamParser;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.ctx.EvaluationCtx;
import org.wso2.balana.ctx.EvaluationCtxFactory;
import org.wso2.balana.finder.PolicyFinder;
import org.wso2.balana.finder.PolicyFinderModule;
import org.wso2.balana.finder.PolicyFinderResult;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Benchmark solely for finding matching policies and policy sets for a particular request
 * under different conditions and configurations.
 * Includes the whole policy finding process, e.g. target matching and loading the policy.
 * Tests only PolicyFinderModule Type 2, ProxyFilebasedPolicyFinderModule from ExtendedPDP,
 * which provides functionalities such as caching of policies, policy finder results
 * and loading policies on-demand.
 */
@State(Scope.Benchmark)
public class CacheFinderBenchmark {
    private static Logger log = LoggerFactory.getLogger(CacheFinderBenchmark.class);

    @Param({ "2" })
    int policyFinderType;

    @Param({ "10000" })
    int requestCount;

    @Param({ "100", "1000", "1010", "10000" })
    int policyCount;

    @Param({ "0", "0.25", "0.5" })
    float finderResultCacheFraction;

    @Param({ "0.5", "1" })
    float finderPolicyCacheFraction;

    List<EvaluationCtx> requests;
    PolicyFinder finder;
    Random random;

    @Setup
    public void setup() throws FileNotFoundException {
        log.info("Setting up FinderBenchmark");
        String policyLocation;
        switch (policyCount) {
            case 100: policyLocation = BenchmarkUtil.POLICIES_100; break;
            case 1000: policyLocation = BenchmarkUtil.POLICIES_1k; break;
            case 1010: policyLocation = BenchmarkUtil.POLICIES_1k10; break;
            case 10000: policyLocation = BenchmarkUtil.POLICIES_10k; break;
            default: throw new IllegalArgumentException("Illegal Policycount " + policyCount);
        }
        log.info("{} policies at path {}", policyCount, policyLocation);
        int resultCacheSize = Math.round(finderResultCacheFraction * requestCount);
        int policyCacheSize = Math.round(finderPolicyCacheFraction * policyCount);
        log.info("ResultCache size is {} | PolicyCache size is {}", resultCacheSize, policyCacheSize);

        PolicyFinderModule pfm = new ProxyFilebasedPolicyFinderModule(policyLocation, resultCacheSize, policyCacheSize);
        log.info("PolicyFinderModule is {}", pfm.getClass().getSimpleName());

        log.info("Loading {} requests", requestCount);
        requests = BenchmarkUtil.loadStrings(BenchmarkUtil.REQUESTS, requestCount).stream()
                .collect(Collectors.mapping(s -> {
                    try { return EvaluationCtxFactory.getFactory().getEvaluationCtx(XACML3StreamParser.readRequest(s), null); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }, Collectors.toList()));
        log.info("Loaded {} requests", requests.size());
        random = new Random();

        Set<PolicyFinderModule> finderModules = new HashSet<>();
        finderModules.add(pfm);
        finder = new PolicyFinder();
        finder.setModules(finderModules);
        finder.init();
        log.info("Ready to go...");
    }

    @Benchmark
    public PolicyFinderResult findPolicyRandomRequest() {
        return finder.findPolicy(requests.get(random.nextInt(requests.size())));
    }

}
