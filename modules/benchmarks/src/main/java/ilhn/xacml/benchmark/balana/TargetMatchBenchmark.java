package ilhn.xacml.benchmark.balana;

import ilhn.xacml.benchmark.BenchmarkUtil;
import ilhn.xacml.util.XACML3StreamParser;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.AbstractPolicy;
import org.wso2.balana.MatchResult;
import org.wso2.balana.ctx.EvaluationCtx;
import org.wso2.balana.ctx.EvaluationCtxFactory;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Benchmark for the target matching performance.
 * Matches policies of different size and composition against requests.
 */
@State(Scope.Benchmark)
public class TargetMatchBenchmark {
    private static Logger log = LoggerFactory.getLogger(TargetMatchBenchmark.class);

    @Param({"100", "1000", "10000"})
    int policyCount;

    List<EvaluationCtx> requests;
    List<AbstractPolicy> policies;
    Random random;

    @Setup
    public void setup() throws FileNotFoundException {
        String policyLocation;
        switch (policyCount) {
            case 100:
                policyLocation = BenchmarkUtil.POLICIES_100;
                break;
            case 1000:
                policyLocation = BenchmarkUtil.POLICIES_1k;
                break;
            case 10000:
                policyLocation = BenchmarkUtil.POLICIES_10k;
                break;
            default:
                throw new IllegalArgumentException("Illegal Policycount " + policyCount);
        }

        log.info("Loading requests");
        requests = BenchmarkUtil.loadStrings(BenchmarkUtil.REQUESTS, 1000).stream()
                .map(s -> { try { return EvaluationCtxFactory.getFactory().getEvaluationCtx(XACML3StreamParser.readRequest(s), null); }
                catch (Exception e) { throw new RuntimeException(e); } })
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        log.info("Loaded {} requests", requests.size());

        log.info("Loading policies");
        policies = BenchmarkUtil.loadStrings(policyLocation).stream()
                .map(s -> { try { return XACML3StreamParser.readPolicyOrPolicySet(s, null); }
                catch (Exception e) { throw new RuntimeException(e); } })
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        log.info("Loaded {} policies", policies.size());

        random = new Random();
    }

    @Benchmark
    public MatchResult matchRandomPolicyRequest() {
        return policies.get(random.nextInt(policies.size()) ).match( requests.get(random.nextInt(requests.size())));
    }

}
