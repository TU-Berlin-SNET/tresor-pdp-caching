package ilhn.xacml.benchmark.balana;

import ilhn.xacml.benchmark.BenchmarkUtil;
import ilhn.xacml.util.XACML3StreamParser;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.ctx.AbstractResult;
import org.wso2.balana.ctx.EvaluationCtx;
import org.wso2.balana.ctx.EvaluationCtxFactory;
import org.wso2.balana.finder.PolicyFinder;
import org.wso2.balana.finder.PolicyFinderModule;
import org.wso2.balana.finder.PolicyFinderResult;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Benchmark solely for evaluating a matching policy or policy set agains a request.
 * Excludes policy finding.
 */
@State(Scope.Benchmark)
public class EvaluationBenchmark {
    private static Logger log = LoggerFactory.getLogger(EvaluationBenchmark.class);

    @Param({"100", "1000", "10000"})
    int policyCount;

    List<EvaluationCtx> requests;
    PolicyFinder finder;
    Random random;

    private PolicyFinderResult finderResult;
    private EvaluationCtx evaluationCtx;

    @Setup
    public void setup() throws FileNotFoundException {
        log.info("Setting up FinderBenchmark");
        String policyLocation;
        switch (policyCount) {
            case 100:
                policyLocation = BenchmarkUtil.POLICIES_100;
                break;
            case 1000:
                policyLocation = BenchmarkUtil.POLICIES_1k;
                break;
            case 1010:
                policyLocation = BenchmarkUtil.POLICIES_1k10;
                break;
            case 10000:
                policyLocation = BenchmarkUtil.POLICIES_10k;
                break;
            default:
                throw new IllegalArgumentException("Illegal Policycount " + policyCount);
        }

        log.info("Loading requests");
        requests = BenchmarkUtil.loadStrings(BenchmarkUtil.REQUESTS, 10000).stream()
                .collect(Collectors.mapping(s -> {
                    try {
                        return EvaluationCtxFactory.getFactory().getEvaluationCtx(XACML3StreamParser.readRequest(s), null);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, Collectors.toList()));
        log.info("Loaded {} requests", requests.size());
        random = new Random();

        log.info("Setting up finder");
        Set<PolicyFinderModule> finderModules = new HashSet<>();
        finderModules.add(new FileBasedPolicyFinderModule(policyLocation));
        finder = new PolicyFinder();
        finder.setModules(finderModules);
        finder.init();
        log.info("Ready to go");
    }

    @Setup(Level.Trial)
    public void setupTrial() {
        evaluationCtx = requests.get(random.nextInt(requests.size()));
        finderResult = finder.findPolicy(evaluationCtx);
    }

    @Benchmark
    public AbstractResult evaluate() {
        return finderResult.getPolicy().evaluate(evaluationCtx);
    }

}

