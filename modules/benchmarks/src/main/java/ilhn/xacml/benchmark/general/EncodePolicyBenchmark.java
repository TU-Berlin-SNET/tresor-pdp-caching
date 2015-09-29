package ilhn.xacml.benchmark.general;

import ilhn.xacml.benchmark.BenchmarkUtil;
import ilhn.xacml.util.XACML3StreamParser;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.AbstractPolicy;
import org.wso2.balana.ParsingException;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark for encoding already parsed policies into strings.
 */
@State(Scope.Benchmark)
public class EncodePolicyBenchmark {
    private static Logger log = LoggerFactory.getLogger(EncodePolicyBenchmark.class);

    Random random;
    AtomicInteger index;
    List<AbstractPolicy> policies;

    @Param({ "100", "1000", "10000" })
    int policyCount;

    @Setup
    public void setup() throws FileNotFoundException, XMLStreamException, ParsingException {
        log.info("Setting up");
        String policyLocation = "";
        switch (policyCount) {
            case 100: policyLocation = BenchmarkUtil.POLICIES_100; break;
            case 1000: policyLocation = BenchmarkUtil.POLICIES_1k; break;
            case 10000: policyLocation = BenchmarkUtil.POLICIES_10k; break;
        }
        log.info("PolicyLocation is {}", policyLocation);

        log.info("Loading policies");
        policies = BenchmarkUtil.loadStrings(policyLocation, 100).stream()
                .map(s -> { try { return XACML3StreamParser.readPolicyOrPolicySet(s, null); }
                catch (Exception e) { throw new RuntimeException(e); } })
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        log.info("Loaded {} policies from {}", policies.size(), policyLocation);

        random = new Random();
        log.info("Ready to go...");
    }

    @Setup(Level.Trial)
    public void setupTrial() {
        index = new AtomicInteger(0);
    }

    @Benchmark
    public String encodeRandomPolicy() {
        return policies.get(random.nextInt(policies.size())).encode();
    }

    @Benchmark
    public String encodeSuccessivePolicy() {
        return policies.get(index.getAndIncrement() % policies.size()).encode();
    }

}
