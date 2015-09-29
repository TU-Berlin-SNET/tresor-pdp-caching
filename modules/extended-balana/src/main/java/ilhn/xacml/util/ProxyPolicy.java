package ilhn.xacml.util;

import org.wso2.balana.*;
import org.wso2.balana.ctx.EvaluationCtx;

public class ProxyPolicy {

    String id;
    AbstractTarget target;
    int refType;

    public ProxyPolicy(AbstractPolicy policy) {
        if (policy == null)
            throw new IllegalArgumentException("policy may not be null");

        this.id = policy.getId().toString();
        this.target = policy.getTarget();

        if (policy instanceof Policy)
            this.refType = PolicyReference.POLICY_REFERENCE;
        if (policy instanceof PolicySet)
            this.refType = PolicyReference.POLICYSET_REFERENCE;
    }

    public ProxyPolicy(String id, AbstractTarget target, int refType) {
        if (id == null)
            throw new IllegalArgumentException("id may not be null");
        if (target == null)
            throw new IllegalArgumentException("target may not be null");
        if (refType != PolicyReference.POLICY_REFERENCE && refType != PolicyReference.POLICYSET_REFERENCE)
            throw new IllegalArgumentException("invalid reference type");

        this.id = id;
        this.target = target;
        this.refType = refType;
    }

    public MatchResult match(EvaluationCtx evaluationCtx) {
        return this.target.match(evaluationCtx);
    }

    public String getId() {
        return id;
    }

    public boolean isPolicy() {
        return refType == PolicyReference.POLICY_REFERENCE;
    }

    public boolean isPolicySet() {
        return refType == PolicyReference.POLICYSET_REFERENCE;
    }

    public int getType() {
        return refType;
    }
}
