package ilhn.xacml.benchmark;

import ilhn.xacml.util.ProxyPolicy;
import ilhn.xacml.util.XACML3StreamParser;
import org.github.jamm.MemoryMeter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wso2.balana.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MemoryBench {
    private static Logger log = LoggerFactory.getLogger(MemoryBench.class);

    public static void main (String[] args) throws IOException, ParsingException, XMLStreamException {
        MemoryMeter meter = new MemoryMeter();

        String[] paths = new String[]{ BenchmarkUtil.POLICIES_100 + File.separator + "Policy00000.xml",
                                       BenchmarkUtil.POLICIES_1k + File.separator + "Policy00000.xml",
                                       BenchmarkUtil.POLICIES_10k + File.separator + "Policy00000.xml"};
        int index = 0;

        String policyFile = paths[index];
        AbstractPolicy streamedPolicy = XACML3StreamParser.readPolicyOrPolicySet(new File(policyFile), null);
        AbstractPolicy domPolicy = loadPolicy(policyFile);
        ProxyPolicy proxyPolicy = XACML3StreamParser.readProxyPolicy(new File(policyFile));

        long[] streamedPolicyMeasurements = new long[]{ meter.measure(streamedPolicy), meter.measureDeep(streamedPolicy), meter.countChildren(streamedPolicy) };
        long[] domPolicyMeasurements = new long[]{ meter.measure(domPolicy), meter.measureDeep(domPolicy), meter.countChildren(domPolicy) };
        long[] proxyPolicyMeasurements = new long[]{ meter.measure(proxyPolicy), meter.measureDeep(proxyPolicy), meter.countChildren(proxyPolicy) };

        int ruleCount= (index == 0) ? 1000 : (index == 1) ? 100 : 10;

        log.info("Index: {}, Policy contains {} rules", index, ruleCount);
        log.info("(AbstractPolicy|StreamParser): measure: {} | measureDeep: {} | countChildren: {}",
                streamedPolicyMeasurements[0], streamedPolicyMeasurements[1], streamedPolicyMeasurements[2]);
        log.info("(AbstractPolicy|DOMParser): measure: {} | measureDeep: {} | countChildren: {}",
                domPolicyMeasurements[0], domPolicyMeasurements[1], domPolicyMeasurements[2]);
        log.info("(ProxyPolicy|StreamParser): measure: {} | measureDeep: {} | countChildren: {}",
                proxyPolicyMeasurements[0], proxyPolicyMeasurements[1], proxyPolicyMeasurements[2]);

        log.info("Finished.");
    }

    private static AbstractPolicy loadPolicy(String policyFile) {
        AbstractPolicy policy = null;
        try (InputStream inputStream = new FileInputStream(new File(policyFile))){
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
