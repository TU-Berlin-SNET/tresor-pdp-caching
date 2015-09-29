package ilhn.xacml.benchmark;

import ilhn.xacml.benchmark.balana.*;
import ilhn.xacml.benchmark.general.EncodePolicyBenchmark;
import ilhn.xacml.benchmark.general.EncodeRequestBenchmark;
import ilhn.xacml.benchmark.general.PolicyParserBenchmark;
import ilhn.xacml.benchmark.general.RequestParserBenchmark;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BenchmarkRunner {
    private static Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    public static void main (String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .warmupIterations(20)
                .measurementIterations(20)
                .forks(2)
                .threads(2)
                // general benchmarks
                .include(EncodePolicyBenchmark.class.getSimpleName())
                .include(EncodeRequestBenchmark.class.getSimpleName())
                .include(PolicyParserBenchmark.class.getSimpleName())
                .include(RequestParserBenchmark.class.getSimpleName())
                // (extended) balana benchmarks
                .include(EvaluationBenchmark.class.getSimpleName())
                .include(NoCacheExtendedPDPBenchmark.class.getSimpleName())
                .include(NoCacheFinderBenchmark.class.getSimpleName())
                .include(CacheExtendedPDPBenchmark.class.getSimpleName())
                .include(CacheFinderBenchmark.class.getSimpleName())
                .include(TargetMatchBenchmark.class.getSimpleName())
                .build();

        new Runner(options).run();
    }

}
