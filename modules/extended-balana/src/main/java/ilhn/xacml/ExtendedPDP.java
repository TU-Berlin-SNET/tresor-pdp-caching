package ilhn.xacml;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import ilhn.xacml.util.XACML3StreamParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.PDP;
import org.wso2.balana.PDPConfig;
import org.wso2.balana.ParsingException;
import org.wso2.balana.ctx.AbstractRequestCtx;
import org.wso2.balana.ctx.ResponseCtx;
import org.wso2.balana.finder.PolicyFinder;
import org.wso2.balana.finder.PolicyFinderResult;

import javax.xml.stream.XMLStreamException;
import java.util.LinkedList;
import java.util.List;

/**
 * PDP employing decision caching techniques, uses balana PDP as back end
 */
public class ExtendedPDP {
    private static Logger log = LoggerFactory.getLogger(ExtendedPDP.class);

    PDP pdp;
    PDPConfig pdpConfig;
    PolicyFinder policyFinder;
    LoadingCache<String, PDPDecision> cache;
    Cache<String, PolicyFinderResult> resultCache;

    public ExtendedPDP(PDPConfig pdpConfig, int cacheSize) {
        pdp = new PDP(pdpConfig);
        this.pdpConfig = pdpConfig;
        policyFinder = this.pdpConfig.getPolicyFinder();
        cache = Caffeine.newBuilder().maximumSize(cacheSize).build(this::evaluateWithCache);
    }

    public String evaluate(String request) {
        log.trace("Cached decision is available: {}", cache.getIfPresent(request) != null );
        PDPDecision decision = cache.get(request);
        /*log.debug("Permit: {}, Deny: {}, Indeterminate: {}, NotApplicable: {}", decision.response.contains("Permit"),
                decision.response.contains("Deny"), decision.response.contains("Indeterminate"),
                decision.response.contains("NotApplicable"));*/
        return decision.response;
    }

    PDPDecision evaluateWithCache(String request) {
        try {
            AbstractRequestCtx requestCtx = XACML3StreamParser.readRequest(request);
            ResponseCtx responseCtx = pdp.evaluate(requestCtx);
            return new PDPDecision(new LinkedList<>(), responseCtx.encode());
        } catch (XMLStreamException | ParsingException e) {
            throw new RuntimeException(e);
        }
    }

    class PDPDecision {
        List<String> matchedPolicies;
        String response;

        public PDPDecision(List<String> matchedPolicies, ResponseCtx decision) {
            this.matchedPolicies = matchedPolicies;
            this.response = decision.encode();
        }

        public PDPDecision(LinkedList<String> matchedPolicies, String responseCtx) {
            this.matchedPolicies = matchedPolicies;
            this.response = responseCtx;
        }

        public String getDecision() { return this.response; }

        public boolean dependsOn(String policyId) {
            return matchedPolicies.contains(policyId);
        }

        public boolean dependsOnAny(List<String> policyIds) {
            return matchedPolicies.contains(policyIds);
        }

        public boolean policiesMatch(List<String> ids) {
            return matchedPolicies.containsAll(ids);
        }
    }

}
