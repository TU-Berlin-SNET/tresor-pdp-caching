package ilhn.xacml.finder;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import ilhn.xacml.util.ProxyPolicy;
import ilhn.xacml.util.XACML3StreamParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.balana.*;
import org.wso2.balana.combine.PolicyCombiningAlgorithm;
import org.wso2.balana.combine.xacml2.DenyOverridesPolicyAlg;
import org.wso2.balana.ctx.EvaluationCtx;
import org.wso2.balana.ctx.Status;
import org.wso2.balana.finder.PolicyFinder;
import org.wso2.balana.finder.PolicyFinderModule;
import org.wso2.balana.finder.PolicyFinderResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PolicyFinderModule using proxy policies (excludes rules, etc. Only policy header and target)
 * and caching.
 */
public class ProxyFilebasedPolicyFinderModule extends PolicyFinderModule {
    private static Logger log = LoggerFactory.getLogger(ProxyFilebasedPolicyFinderModule.class);

    String policyDir;
    PolicyFinder finder = null;
    PolicyCombiningAlgorithm combiningAlg;

    Map<String, ProxyPolicy> policyMap;
    Cache<String, CachedFinderResult> resultCache;
    LoadingCache<String, AbstractPolicy> policyCache;

    public ProxyFilebasedPolicyFinderModule(String policyLocation, int resultCacheSize, int policyCacheSize) {
        policyMap = new HashMap<>();
        policyDir = (policyLocation.endsWith(File.separator)) ? policyLocation : policyLocation + File.separator;

        resultCache = Caffeine.newBuilder().maximumSize(resultCacheSize).build();
        policyCache = Caffeine.newBuilder().maximumSize(policyCacheSize).build(s -> loadPolicy(s, finder));

        log.info("initialized ProxyPolicyFinder, resultCache: {}, policyCache: {}, path: {}", resultCacheSize, policyCacheSize, policyDir);
    }

    @Override
    public void init(PolicyFinder finder) {
        this.finder = finder;
        populatePolicies();
        combiningAlg = new DenyOverridesPolicyAlg();
    }

    @Override
    public PolicyFinderResult findPolicy(EvaluationCtx context) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        context.getRequestCtx().encode(out);
        String request = new String(out.toByteArray());

        CachedFinderResult cachedFinderResult = resultCache.getIfPresent(request);
        if (cachedFinderResult != null) {
            log.debug("Returning from finder cache");
            return createPolicyFinderResult(cachedFinderResult);
        }

        List<AbstractPolicy> selectedPolicies = new ArrayList<>();

        for (Map.Entry<String, ProxyPolicy> entry: policyMap.entrySet()) {
            ProxyPolicy proxyPolicy = entry.getValue();
            MatchResult match = proxyPolicy.match(context);
            int result = match.getResult();

            if (result == MatchResult.NO_MATCH)
                continue;

            if (result == MatchResult.INDETERMINATE) {
                PolicyFinderResult finderResult = new PolicyFinderResult(match.getStatus());
                resultCache.put(request, new CachedFinderResult(finderResult));
                return finderResult;
            }

            if (result == MatchResult.MATCH)
                selectedPolicies.add(policyCache.get(proxyPolicy.getId()));
        }

        PolicyFinderResult finderResult;
        switch (selectedPolicies.size()) {
            case 0:
                finderResult = new PolicyFinderResult();
                break;
            case 1:
                finderResult = new PolicyFinderResult(selectedPolicies.get(0));
                break;
            default:
                finderResult = new PolicyFinderResult(new PolicySet(null, combiningAlg, null, selectedPolicies));
                break;
        }
        resultCache.put(request, new CachedFinderResult(finderResult));
        return finderResult;
    }

    @Override
    public PolicyFinderResult findPolicy(URI idReference, int type, VersionConstraints constraints,
                                         PolicyMetaData parentMetaData) {

        ProxyPolicy proxyPolicy = policyMap.get(idReference.toString());
        if (proxyPolicy != null && type == proxyPolicy.getType())
            return new PolicyFinderResult(policyCache.get(proxyPolicy.getId()));

        // if there was an error loading the policy, return the error
        ArrayList<String> code = new ArrayList<>();
        code.add(Status.STATUS_PROCESSING_ERROR);
        Status status = new Status(code,
                "couldn't load referenced policy");
        return new PolicyFinderResult(status);
    }

    private PolicyFinderResult createPolicyFinderResult(CachedFinderResult cachedFinderResult) {
        if (cachedFinderResult.status != null)
            return new PolicyFinderResult(cachedFinderResult.status);

        if (cachedFinderResult.selectIds != null) {
            switch (cachedFinderResult.selectIds.size()) {
                case 0 : return new PolicyFinderResult();
                case 1 :
                    AbstractPolicy policy = loadPolicy(cachedFinderResult.selectIds.get(0), finder);
                    return new PolicyFinderResult(policy);
                default :
                    List<AbstractPolicy> policies = new ArrayList<>(cachedFinderResult.selectIds.size());
                    for (String id : cachedFinderResult.selectIds)
                        policies.add(policyCache.get(id));
                    return new PolicyFinderResult(new PolicySet(null, combiningAlg, null, policies), cachedFinderResult.selectIds);
            }
        }

        return new PolicyFinderResult();
    }

    @Override
    public boolean isIdReferenceSupported() {
        return true;
    }

    @Override
    public boolean isRequestSupported() {
        return true;
    }

    void populatePolicies() {
        File basedir = new File(policyDir);
        if (!basedir.isDirectory())
            throw new RuntimeException("Invalid path given, not a directory. " + policyDir);

        try {
            for (File f : basedir.listFiles()) {
                ProxyPolicy proxyPolicy = XACML3StreamParser.readProxyPolicy(f);
                policyMap.put(proxyPolicy.getId(), proxyPolicy);
                policyCache.get(proxyPolicy.getId());
            }
        } catch (Exception e) {
            log.error("Fatal error, failed to populate policies", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Private helper that tries to load the given file-based policy, and
     * returns null if any error occurs.
     *
     * @param policyId id and filename of the policy
     * @param finder policy finder
     * @return  <code>AbstractPolicy</code>
     */
    AbstractPolicy loadPolicy(String policyId, PolicyFinder finder) {
        AbstractPolicy policy = null;
        try {
            policy = XACML3StreamParser.readPolicyOrPolicySet(new File(policyDir + policyId), finder);
        } catch (Exception e) {
            log.error("Failed to load/parse policy!", e);
        }
        return policy;
    }

    MatchInfo combineMatches(MatchInfo m1, MatchInfo m2) {
        if (m1 == null) return (m2 == null) ? new MatchInfo() : m2;
        if (m2 == null) return m1;

        if (m1.isIndeterminate()) return m1;
        if (m2.isIndeterminate()) return m2;

        if (m1.isMatch()) return (m2.isMatch()) ? m1.putAll(m2.selectMap) : m1;

        return m2;
    }

    class MatchInfo {
        Map<String, AbstractPolicy> selectMap;
        MatchResult matchResult;

        MatchInfo() {
            selectMap = new HashMap<>(1);
            matchResult = new MatchResult(MatchResult.NO_MATCH);
        }

        MatchInfo(ProxyPolicy proxyPolicy, MatchResult matchResult) {
            this();

            if (matchResult.getResult() == MatchResult.MATCH) {
                log.trace("Policy is present in cache: {}", policyCache.getIfPresent(proxyPolicy.getId()) != null);
                selectMap.put(proxyPolicy.getId(), policyCache.get(proxyPolicy.getId()));
            }

            this.matchResult = matchResult;
        }

        MatchInfo putAll(Map<String, AbstractPolicy> selectMap) {
            this.selectMap.putAll(selectMap);
            return this;
        }

        List<String> getSelectIds() {
            return new ArrayList<>(selectMap.keySet());
        }

        List<AbstractPolicy> getSelectPolicies() {
            return new ArrayList<>(selectMap.values());
        }

        boolean isMatch() {
            return matchResult.getResult() == MatchResult.MATCH;
        }

        boolean isNoMatch() {
            return matchResult.getResult() == MatchResult.NO_MATCH;
        }

        boolean isIndeterminate() {
            return matchResult.getResult() == MatchResult.INDETERMINATE;
        }
    }

    class CachedFinderResult {
        Status status;
        List<String> selectIds;

        public CachedFinderResult(PolicyFinderResult finderResult) {
            status = finderResult.getStatus();
            selectIds = finderResult.getPolicyIds();
        }
    }
}
