package ilhn.xacml.benchmark.general;

import ilhn.xacml.benchmark.BenchmarkUtil;
import ilhn.xacml.util.XACML3StreamParser;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.ctx.AbstractRequestCtx;
import org.wso2.balana.ctx.xacml3.RequestCtx;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark for encoding parsed requests as strings.
 */
@State(Scope.Benchmark)
public class EncodeRequestBenchmark {
    private static Logger log = LoggerFactory.getLogger(EncodeRequestBenchmark.class);

    Random random;
    AtomicInteger index;
    List<RequestCtx> requests;

    private String requestLocation = BenchmarkUtil.REQUESTS;

    @Setup
    public void setup() throws FileNotFoundException {
        Random random = new Random();

        log.info("Loading requests...");
        requests = BenchmarkUtil.loadStrings(requestLocation, 1000).stream()
                .map(s -> { try { return XACML3StreamParser.readRequest(s); }
                catch (Exception e) { throw new RuntimeException(e); } })
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        log.info("Loaded {} requests from {}", requests.size(), requestLocation);
        log.info("Ready to go...");
    }

    @Setup(Level.Trial)
    public void setupTrial() {
        index = new AtomicInteger(0);
    }

    @Benchmark
    public String encodeRandomRequest() {
        AbstractRequestCtx requestCtx = requests.get(random.nextInt(requests.size()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        requestCtx.encode(out);
        return new String(out.toByteArray());
        //return requests.get(random.nextInt(requests.size())).encode();
    }

    @Benchmark
    public String encodeSuccessiveRequest() {
        AbstractRequestCtx requestCtx = requests.get(index.getAndIncrement() % requests.size());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        requestCtx.encode(out);
        return new String(out.toByteArray());
    }

}
