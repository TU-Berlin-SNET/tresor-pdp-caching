package ilhn.xacml.benchmark.general;

import ilhn.xacml.benchmark.BenchmarkUtil;
import ilhn.xacml.util.XACML3StreamParser;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wso2.balana.AbstractPolicy;
import org.wso2.balana.DOMHelper;
import org.wso2.balana.Policy;
import org.wso2.balana.PolicySet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.List;
import java.util.Random;

@State(Scope.Benchmark)
public class PolicyParserBenchmark {
    private static Logger log = LoggerFactory.getLogger(PolicyParserBenchmark.class);

    Random random;
    List<String> policies;

    @Param({"100", "1000", "10000"})
    int policyCount;

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
        log.info("Loading policies from {}", policyLocation);
        policies = BenchmarkUtil.loadStrings(policyLocation, 100);
        random = new Random();
        log.info("Loaded {} policies from ", policies.size(), policyLocation);

        log.info("Ready to go...");
    }

    @Benchmark
    public AbstractPolicy domParseRandomPolicy() {
        return loadPolicy(policies.get(random.nextInt(policies.size())));
    }

    @Benchmark
    public AbstractPolicy streamParseRandomPolicy() throws Exception {
        return XACML3StreamParser.readPolicyOrPolicySet(policies.get(random.nextInt(policies.size())), null);
    }

    private static AbstractPolicy loadPolicy(String policyString) {
        AbstractPolicy policy = null;
        try (InputStream inputStream = new ByteArrayInputStream(policyString.getBytes())){
            // create the factory
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringComments(true);
            factory.setNamespaceAware(true);
            factory.setValidating(false);

            // create a builder based on the factory & try to load the policy
            DocumentBuilder db = factory.newDocumentBuilder();
            Document doc = db.parse(inputStream);

            // handle the policy, if it's a known type
            Element root = doc.getDocumentElement();
            String name = DOMHelper.getLocalName(root);

            if (name.equals("Policy")) {
                policy = Policy.getInstance(root);
            } else if (name.equals("PolicySet")) {
                policy = PolicySet.getInstance(root, null);
            }
        } catch (Exception e) {
            // just only logs
            log.error("Failed to load policy!", e);
        }

        return policy;
    }

}
