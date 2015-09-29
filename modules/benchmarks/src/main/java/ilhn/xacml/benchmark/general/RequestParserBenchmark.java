package ilhn.xacml.benchmark.general;

import ilhn.xacml.benchmark.BenchmarkUtil;
import ilhn.xacml.util.XACML3StreamParser;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.ParsingException;
import org.wso2.balana.ctx.AbstractRequestCtx;
import org.wso2.balana.ctx.RequestCtxFactory;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
public class RequestParserBenchmark {
    private static Logger log = LoggerFactory.getLogger(RequestParserBenchmark.class);

    Random random;
    List<String> requests;
    AtomicInteger index;

    @Setup
    public void setup() throws FileNotFoundException {
        log.info("Setting up...");
        random = new Random();
        requests = BenchmarkUtil.loadStrings(BenchmarkUtil.REQUESTS, 1000);
        log.info("Loaded {} requests", requests.size());
    }

    @Setup(Level.Trial)
    public void setupTrial() {
        index = new AtomicInteger(0);
    }

    @Benchmark
    public AbstractRequestCtx streamParseRandomRequest() throws FileNotFoundException, XMLStreamException, ParsingException {
        return XACML3StreamParser.readRequest(requests.get(random.nextInt(requests.size())));
    }

    @Benchmark
    public AbstractRequestCtx domParseRandomRequest() throws FileNotFoundException, ParsingException {
        return RequestCtxFactory.getFactory().getRequestCtx(requests.get(random.nextInt(requests.size())));
    }

    @Benchmark
    public AbstractRequestCtx streamParseSuccessiveRequest() throws FileNotFoundException, XMLStreamException, ParsingException {
        return XACML3StreamParser.readRequest(requests.get(index.getAndIncrement() % requests.size()));
    }

    @Benchmark
    public AbstractRequestCtx domParseSuccessiveRequest() throws FileNotFoundException, ParsingException {
        return RequestCtxFactory.getFactory().getRequestCtx(requests.get(index.getAndIncrement() % requests.size()));
    }

}
