package ilhn.xacml.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.wso2.balana.*;
import org.wso2.balana.attr.AttributeValue;
import org.wso2.balana.attr.xacml3.AttributeDesignator;
import org.wso2.balana.attr.xacml3.AttributeSelector;
import org.wso2.balana.combine.CombiningAlgorithm;
import org.wso2.balana.combine.PolicyCombiningAlgorithm;
import org.wso2.balana.combine.RuleCombiningAlgorithm;
import org.wso2.balana.cond.*;
import org.wso2.balana.ctx.Attribute;
import org.wso2.balana.ctx.xacml3.RequestCtx;
import org.wso2.balana.ctx.xacml3.Result;
import org.wso2.balana.finder.PolicyFinder;
import org.wso2.balana.xacml3.*;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class XACML3StreamParser {
    private static Logger log = LoggerFactory.getLogger(XACML3StreamParser.class);

    private static XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
    private static String[] XMLEVENTS = { "", "Start Element", "End Element", "Processing Instruction", "Characters",
            "Comment", "Space", "Start Document", "End Document", "Entity Reference", "Attribute", "DTD", "CDATA",
            "Namespace", "Notation Declaration", "Entity Declaration"};

    private interface ParseFunction<T, R> {
        R apply(T t) throws ParsingException, XMLStreamException;
    }

    private interface BiParseFunction<T, U, R> {
        R apply(T t, U u) throws ParsingException, XMLStreamException;
    }

    public static RequestCtx readRequest(InputStream inputStream) throws XMLStreamException, ParsingException {
        return readRequest(createReader(inputStream));
    }
    public static RequestCtx readRequest(String request) throws XMLStreamException, ParsingException {
        try (StringReader stringReader = new StringReader(request)) {
            return readRequest(createReader(stringReader));
        }
    }
    public static RequestCtx readRequest(File file) throws IOException, XMLStreamException, ParsingException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return readRequest(createReader(inputStream));
        }
    }
    private static RequestCtx readRequest(XMLStreamReader reader) throws ParsingException, XMLStreamException {
        return parse(reader, XACML3StreamParser::parseRequest, RequestCtx.class);
    }

    public static ProxyPolicy readProxyPolicy(InputStream inputStream) throws XMLStreamException, ParsingException {
        return readProxyPolicy(createReader(inputStream));
    }
    public static ProxyPolicy readProxyPolicy(String policy) throws XMLStreamException, ParsingException {
        try (StringReader stringReader = new StringReader(policy)) {
            return readProxyPolicy(createReader(stringReader));
        }
    }
    public static ProxyPolicy readProxyPolicy(File file) throws IOException, XMLStreamException, ParsingException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return readProxyPolicy(createReader(inputStream));
        }
    }
    private static ProxyPolicy readProxyPolicy(XMLStreamReader reader) throws XMLStreamException, ParsingException {
        return parse(reader, XACML3StreamParser::parseProxyPolicy, ProxyPolicy.class);
    }

    public static PolicySet readPolicySet(String policySet, PolicyFinder policyFinder)
            throws XMLStreamException, ParsingException {
        try (StringReader stringReader = new StringReader(policySet)) {
            return readPolicySet(createReader(stringReader), policyFinder);
        }
    }
    public static PolicySet readPolicySet(File file, PolicyFinder policyFinder)
            throws IOException, XMLStreamException, ParsingException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return readPolicySet(inputStream, policyFinder);
        }
    }
    public static PolicySet readPolicySet(InputStream inputStream, PolicyFinder policyFinder)
            throws XMLStreamException, ParsingException {
        return readPolicySet(createReader(inputStream), policyFinder);
    }
    private static PolicySet readPolicySet(XMLStreamReader reader, PolicyFinder policyFinder)
            throws XMLStreamException, ParsingException {
        ParseFunction<PolicyFinder, ParseFunction<XMLStreamReader, PolicySet>> f =
                finder -> xmlreader -> ( parsePolicySet(xmlreader, finder) );
        return parse(reader, f.apply(policyFinder), PolicySet.class);
    }

    public static Policy readPolicy(String policy) throws XMLStreamException, ParsingException {
        try (StringReader stringReader = new StringReader(policy)) {
            return readPolicy(createReader(stringReader));
        }
    }
    public static Policy readPolicy(File file) throws IOException, XMLStreamException, ParsingException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return readPolicy(inputStream);
        }
    }
    public static Policy readPolicy(InputStream inputStream) throws XMLStreamException, ParsingException {
        return readPolicy(createReader(inputStream));
    }
    private static Policy readPolicy(XMLStreamReader reader) throws XMLStreamException, ParsingException {
        return parse(reader, XACML3StreamParser::parsePolicy, Policy.class);
    }

    public static AbstractPolicy readPolicyOrPolicySet(String policyOrPolicySet, PolicyFinder policyFinder)
            throws XMLStreamException, ParsingException {
        try (StringReader stringReader = new StringReader(policyOrPolicySet)) {
            return readPolicyOrPolicySet( createReader(stringReader), policyFinder );
        }
    }
    public static AbstractPolicy readPolicyOrPolicySet(File file, PolicyFinder policyFinder)
            throws IOException, XMLStreamException, ParsingException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return readPolicyOrPolicySet( inputStream, policyFinder );
        }
    }
    public static AbstractPolicy readPolicyOrPolicySet(InputStream inputStream, PolicyFinder policyFinder)
            throws XMLStreamException, ParsingException {
        return readPolicyOrPolicySet( createReader(inputStream), policyFinder );
    }
    private static AbstractPolicy readPolicyOrPolicySet(XMLStreamReader reader, PolicyFinder policyFinder)
            throws ParsingException, XMLStreamException {
        if (!reader.isStartElement())
            throw unexpectedElement("StartElement Policy or PolicySet", XMLEVENTS[reader.getEventType()]);

        String name = reader.getLocalName();
        switch (name) {
            case "Policy": return readPolicy(reader);
            case "PolicySet": return readPolicySet(reader, policyFinder);
            default: throw unexpectedElement("Policy or PolicySet", name);
        }
    }

    private static <R> R parse(XMLStreamReader reader,
            ParseFunction<XMLStreamReader, R> parseFunction, Class<R> type) throws ParsingException, XMLStreamException {
        try {
            return type.cast(parseFunction.apply(reader));
        } finally {
            try { reader.close(); }
            catch (Exception e) { log.warn("Failed to close reader", e); }
        }
    }

    private static XMLStreamReader createReader(InputStream inputStream) throws XMLStreamException {
        XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(inputStream);
        xmlStreamReader = xmlInputFactory.createFilteredReader(xmlStreamReader, event -> (
                event.isStartElement() || event.isEndElement()));

        return xmlStreamReader;
    }

    private static XMLStreamReader createReader(Reader reader) throws XMLStreamException {
        XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(reader);
        xmlStreamReader = xmlInputFactory.createFilteredReader(xmlStreamReader, event -> (
                event.isStartElement() || event.isEndElement()));

        return xmlStreamReader;
    }

    //-------------------------------------

    private static RequestCtx parseRequest(XMLStreamReader reader) throws ParsingException, XMLStreamException {
        checkProperStart("Request", reader);

        String returnPolicyIdListValue = reader.getAttributeValue(null, "ReturnPolicyIdList");
        if (!isBoolean(returnPolicyIdListValue))
            throw failedToParseRequiredAttribute("ReturnPolicyIdList", null);
        boolean returnPolicyIdList = Boolean.parseBoolean(returnPolicyIdListValue);

        String combinedDecisionValue = reader.getAttributeValue(null, "CombinedDecision");
        if (!isBoolean(combinedDecisionValue))
            throw failedToParseRequiredAttribute("CombinedDecision", null);
        boolean combinedDecision = Boolean.parseBoolean(combinedDecisionValue);
        if (combinedDecision)
            throw new ParsingException("MultipleDecisionProfile is not supported!");

        RequestDefaults requestDefaults = null;
        Set<Attributes> attributes = new LinkedHashSet<>();

        while (reader.hasNext()) {
            reader.next();

            if (reader.isEndElement())
                break;

            String name = reader.getLocalName();
            switch (name) {
                case "RequestDefaults":
                    if (requestDefaults != null)
                        throw failedToCreate("Request. Too many RequestDefaults.", null);
                    requestDefaults = new RequestDefaults(parseDefaults(reader, "RequestDefauts"));
                    break;
                case "Attributes":
                    attributes.add(parseAttributes(reader));
                    break;
                case "MultiRequests": throw new ParsingException("MultiRequests element is unsupported!");
                default: throw unexpectedElement("Request", name);
            }
        }

        if (attributes.isEmpty())
            throw failedToParseRequiredAttribute("Attributes", null);

        return new RequestCtx(null, attributes, returnPolicyIdList, combinedDecision, null, requestDefaults);
    }

    private static Attributes parseAttributes(XMLStreamReader reader) throws ParsingException, XMLStreamException {
        checkProperStart("Attributes", reader);

        URI category;
        try {
            category = new URI(reader.getAttributeValue(null, "Category"));
        } catch (URISyntaxException e) {
            throw failedToParseRequiredAttribute("Category", e);
        }

        String id = reader.getAttributeValue(null, "id");
        Node content = null;
        Set<Attribute> attributes = new HashSet<>();

        while (reader.hasNext()) {
            reader.next();

            if (reader.isEndElement())
                break;

            String name = reader.getLocalName();
            switch (name) {
                case "Content": throw new ParsingException("Unsupported Element: XPath Content");
                case "Attribute":
                    attributes.add(parseAttribute(reader));
                    break;
                default: throw unexpectedElement("Attributes", name);
            }
        }

        return new Attributes(category, content, attributes, id);
    }

    private static Attribute parseAttribute(XMLStreamReader reader) throws ParsingException, XMLStreamException {
        checkProperStart("Attribute", reader);

        URI id;
        try {
            id = new URI(reader.getAttributeValue(null, "AttributeId"));
        } catch (URISyntaxException e) {
            throw failedToParseRequiredAttribute("Attributeid", e);
        }

        String issuer = reader.getAttributeValue(null, "Issuer");
        String includeInResultValue = reader.getAttributeValue(null, "IncludeInResult");
        if (!isBoolean(includeInResultValue))
            throw failedToParseRequiredAttribute("IncludeInResult", null);
        boolean includeInResult = Boolean.parseBoolean(includeInResultValue);

        List<AttributeValue> attributeValues = parseLoop(reader, null,
                (streamReader, xpathVersion) -> parseAttributeValue(streamReader));

        if (attributeValues.isEmpty())
            throw failedToParseRequiredAttribute("AttributeValue", null);

        URI datatype = attributeValues.get(0).getType();
        for (AttributeValue attributeValue : attributeValues) {
            if (!datatype.equals(attributeValue.getType()))
                throw new ParsingException("Attribute MUST contain only AttributeValues of the same type");
        }

        return new Attribute(id, datatype, issuer, null, attributeValues, includeInResult, XACMLConstants.XACML_VERSION_3_0);
    }

    private static ProxyPolicy parseProxyPolicy(XMLStreamReader reader) throws ParsingException, XMLStreamException {
        if (!reader.isStartElement())
            throw cantCreateFrom("AbstractProxyPolicy", XMLEVENTS[reader.getEventType()]);

        int type;
        String idAttribute;

        String name = reader.getLocalName();
        switch (name) {
            case "Policy":
                type = PolicyReference.POLICY_REFERENCE;
                idAttribute = "PolicyId";
                break;
            case "PolicySet":
                type = PolicyReference.POLICYSET_REFERENCE;
                idAttribute = "PolicySetId";
                break;
            default: throw cantCreateFrom("AbstractProxyPolicy", name);
        }

        URI idUri;
        try {
            idUri = new URI(reader.getAttributeValue(null, idAttribute));
        } catch (URISyntaxException e) {
            throw failedToParseRequiredAttribute(idAttribute, e);
        }

        AbstractTarget target = null;
        while (reader.hasNext()) {
            reader.next();
            if (reader.isStartElement() && reader.getLocalName().equals("Target")) {
                target = parseTarget(reader, null);
                break;
            }
        }

        if (target == null)
            throw failedToCreate("AbstractProxyPolicy", null);

        return new ProxyPolicy(idUri.toString(), target, type);
    }

    private static PolicySet parsePolicySet(XMLStreamReader reader, PolicyFinder policyFinder)
            throws ParsingException, XMLStreamException {
        checkProperStart("PolicySet", reader);

        URI id;
        try { id = new URI(reader.getAttributeValue(null, "PolicySetId")); }
        catch (URISyntaxException e) { throw failedToParseRequiredAttribute("PolicySetId", e); }

        String version = reader.getAttributeValue(null, "Version");
        if (version == null)
            throw failedToParseRequiredAttribute("Version", null);

        PolicyCombiningAlgorithm combiningAlg;
        try {
            CombiningAlgorithm algorithm = Balana.getInstance().getCombiningAlgFactory().createAlgorithm(
                    new URI(reader.getAttributeValue(null, "PolicyCombiningAlgId")));

            if (algorithm instanceof PolicyCombiningAlgorithm)
                combiningAlg = (PolicyCombiningAlgorithm) algorithm;
            else
                throw new ParsingException("Combining algorithm in policyset MUST be a policy combining algorithm");
        } catch (UnknownIdentifierException | URISyntaxException e) {
            throw failedToParseRequiredAttribute("PolicyCombiningAlgId", e);
        }

        String description = null;
        String defaultVersion = null;
        AbstractTarget target = null;
        List<AbstractPolicy> policies = new LinkedList<>();
        Set<AbstractObligation> obligations = null;
        Set<AdviceExpression> advices = null;
        PolicyMetaData policyMetaData = new PolicyMetaData(XACMLConstants.XACML_3_0_IDENTIFIER, null);

        while (reader.hasNext()) {
            reader.next();

            if (reader.isEndElement())
                break;

            String name = reader.getLocalName();
            switch (name) {
                case "Description":
                    description = parseDescription(reader);
                    break;
                case "PolicySetDefaults":
                    defaultVersion = parseDefaults(reader, "PolicySetDefaults");
                    policyMetaData = new PolicyMetaData(XACMLConstants.XACML_3_0_IDENTIFIER, defaultVersion);
                    break;
                case "Target":
                    target = parseTarget(reader, defaultVersion);
                    break;
                case "PolicySet":
                    policies.add(parsePolicy(reader));
                    break;
                case "Policy":
                    policies.add(parsePolicy(reader));
                    break;
                case "PolicySetIdReference":
                    policies.add(parsePolicyReference(reader, name, PolicyReference.POLICYSET_REFERENCE,
                            policyMetaData, policyFinder));
                    break;
                case "PolicyIdReference":
                    policies.add(parsePolicyReference(reader, name, PolicyReference.POLICY_REFERENCE,
                            policyMetaData, policyFinder));
                    break;
                case "ObligationExpressions":
                    obligations = parseObligationExpressions(reader, null, defaultVersion);
                    break;
                case "AdviceExpressions":
                    advices = parseAdviceExpressions(reader, null, defaultVersion);
                    break;
                default: throw unexpectedElement("PolicySet", name);
            }
        }

        if (target == null)
            throw failedToParseRequiredAttribute("Target", null);

        return new PolicySet(id, version, combiningAlg,
                new PolicyMetaData(XACMLConstants.XACML_3_0_IDENTIFIER, defaultVersion),
                description, target, policies, defaultVersion, obligations, advices, null);
    }

    private static Policy parsePolicy(XMLStreamReader reader) throws ParsingException, XMLStreamException {
        checkProperStart("Policy", reader);

        URI id;
        try { id = new URI(reader.getAttributeValue(null, "PolicyId")); }
        catch (URISyntaxException e) { throw failedToParseRequiredAttribute("PolicyId", e); }

        String version = reader.getAttributeValue(null, "Version");
        if (version == null)
            throw failedToParseRequiredAttribute("Version", null);

        RuleCombiningAlgorithm combiningAlg;
        try {
            CombiningAlgorithm algorithm = Balana.getInstance().getCombiningAlgFactory().createAlgorithm(
                    new URI(reader.getAttributeValue(null, "RuleCombiningAlgId")));

            if (algorithm instanceof RuleCombiningAlgorithm)
                combiningAlg = (RuleCombiningAlgorithm) algorithm;
            else
                throw new ParsingException("Combining algorithm in policy MUST be a rule combining algorithm");
        } catch (UnknownIdentifierException | URISyntaxException e) {
            throw failedToParseRequiredAttribute("RuleCombiningAlgId", e);
        }

        String description = null;
        AbstractTarget target = null;
        String defaultVersion = null;
        List<Rule> rules = new LinkedList<>();
        Set<AbstractObligation> obligations = null;
        Set<AdviceExpression> advices = null;
        Map<String, VariableDefinition> variableMap = new HashMap<>();

        while(reader.hasNext()) {
            reader.next();

            if (reader.isEndElement())
                break;

            String name = reader.getLocalName();
            switch (name) {
                case "Description":
                    description = parseDescription(reader);
                    break;
                case "PolicyDefaults":
                    defaultVersion = parseDefaults(reader, "PolicyDefaults");
                    break;
                case "Target":
                    target = parseTarget(reader, defaultVersion);
                    break;
                case "VariableDefinition":
                    VariableDefinition variableDefinition = parseVariableDefinition(reader, variableMap, defaultVersion);
                    if (variableMap.containsKey(variableDefinition.getVariableId()))
                        throw new ParsingException("Duplicate variableId " + variableDefinition.getVariableId());

                    variableMap.put(variableDefinition.getVariableId(), variableDefinition);
                    break;
                case "Rule":
                    Rule rule = parseRule(reader, variableMap, defaultVersion);
                    rules.add(rule);
                    break;
                case "ObligationExpressions":
                    obligations = parseObligationExpressions(reader, variableMap, defaultVersion);
                    break;
                case "AdviceExpressions":
                    advices = parseAdviceExpressions(reader, variableMap, defaultVersion);
                    break;
                default:
                    throw unexpectedElement("Policy", name);
            }
        }

        if (target == null)
            throw failedToParseRequiredAttribute("Target", null);

        Set<VariableDefinition> variableDefinitions = new LinkedHashSet<>(variableMap.values());

        return new Policy(id, version, combiningAlg, description,
                new PolicyMetaData(XACMLConstants.XACML_3_0_IDENTIFIER, defaultVersion),
                target, defaultVersion, rules, obligations, advices, variableDefinitions);
    }

    private static PolicyReference parsePolicyReference(XMLStreamReader reader, String refName, int refType,
                   PolicyMetaData policyMetaData, PolicyFinder policyFinder) throws ParsingException, XMLStreamException {
        checkProperStart(refName, reader);
        String version = reader.getAttributeValue(null, "Version");
        String earliestVersion = reader.getAttributeValue(null, "EarliestVersion");
        String latestVersion = reader.getAttributeValue(null, "LatestVersion");

        URI ref;
        try {
            ref = new URI(reader.getElementText());
        } catch (URISyntaxException e) {
            throw failedToCreate(refName, e);
        }

        VersionConstraints versionConstraints = new VersionConstraints(version, earliestVersion, latestVersion);

        return new PolicyReference(ref, refType, versionConstraints, policyFinder, policyMetaData);
    }

    private static String parseDescription(XMLStreamReader reader) throws ParsingException, XMLStreamException {
        checkProperStart("Description", reader);
        return reader.getElementText();
    }

    private static String parseDefaults(XMLStreamReader reader, String defaultsName) throws ParsingException, XMLStreamException {
        checkProperStart(defaultsName, reader);
        reader.next();
        if (reader.isEndElement())
            return null;

        checkProperStart("XPathVersion", reader);
        String defaultVersion = reader.getElementText();
        reader.next();
        return defaultVersion;
    }

    private static AbstractTarget parseTarget(XMLStreamReader reader, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("Target", reader);
        List<AnyOfSelection> anyOfSelections = parseLoop(reader, defaultVersion, XACML3StreamParser::parseAnyOfSelection);

        return new Target(anyOfSelections);
    }

    private static AnyOfSelection parseAnyOfSelection(XMLStreamReader reader, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("AnyOf", reader);
        List<AllOfSelection> allOfSelections = parseLoop(reader, defaultVersion, XACML3StreamParser::parseAllOfSelection);

        if (allOfSelections.isEmpty())
            throw mustContain("AnyOf", "AllOf");

        return new AnyOfSelection(allOfSelections);
    }

    private static AllOfSelection parseAllOfSelection(XMLStreamReader reader, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("AllOf", reader);
        List<TargetMatch> targetMatches = parseLoop(reader, defaultVersion, XACML3StreamParser::parseMatch);

        if (targetMatches.isEmpty())
            throw mustContain("AllOf", "Match");

        return new AllOfSelection(targetMatches);
    }

    private static TargetMatch parseMatch(XMLStreamReader reader, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("Match", reader);

        Function function;
        Evaluatable evaluatable;

        try {
            URI matchId = new URI(reader.getAttributeValue(null, "MatchId"));
            function = FunctionFactory.getTargetInstance().createFunction(matchId);
        } catch (URISyntaxException | UnknownIdentifierException | FunctionTypeException e) {
            // TODO FunctionTypeException might point to a possible abstract function, currently unsupported because node is required for parsing
            throw failedToParseRequiredAttribute("MatchId", e);
        }

        reader.next();
        checkProperStart("AttributeValue", reader);
        AttributeValue attributeValue = parseAttributeValue(reader);

        reader.next();
        switch (reader.getLocalName()) {
            case "AttributeDesignator":
                evaluatable = parseAttributeDesignator(reader);
                break;
            case "AttributeSelector":
                evaluatable = parseAttributeSelector(reader, defaultVersion);
                break;
            default:
                throw unexpectedElement("Match", reader.getLocalName());
        }

        // to point to end element of Match
        reader.next();

        return new TargetMatch(function, evaluatable, attributeValue);
    }

    private static AttributeValue parseAttributeValue(XMLStreamReader reader) throws ParsingException, XMLStreamException {
        checkProperStart("AttributeValue", reader);
        try {
            URI datatype = new URI(reader.getAttributeValue(null, "DataType"));
            String value = reader.getElementText();

            return Balana.getInstance().getAttributeFactory().createValue(datatype, value);
        } catch (URISyntaxException | UnknownIdentifierException e) {
            throw failedToCreate("AttributeValue", e);
        }
    }

    private static AttributeDesignator parseAttributeDesignator(XMLStreamReader reader) throws ParsingException, XMLStreamException {
        checkProperStart("AttributeDesignator", reader);
        try {
            URI datatype = new URI(reader.getAttributeValue(null, "DataType"));
            URI attributeId = new URI(reader.getAttributeValue(null, "AttributeId"));
            URI category = new URI(reader.getAttributeValue(null, "Category"));

            String mustBePresentValue = reader.getAttributeValue(null, "MustBePresent");
            if (mustBePresentValue == null || (!mustBePresentValue.equalsIgnoreCase("true") && !mustBePresentValue.equalsIgnoreCase("false")))
                throw failedToParseRequiredAttribute("MustBePresent", null);
            boolean mustBePresent = Boolean.parseBoolean(mustBePresentValue);

            reader.next();
            return new AttributeDesignator(datatype, attributeId, mustBePresent, category);
        } catch (URISyntaxException e) {
            throw failedToCreate("AttributeDesignator", e);
        }
    }

    private static AttributeSelector parseAttributeSelector(XMLStreamReader reader, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("AttributeSelector", reader);
        if (defaultVersion == null)
            throw failedToParseRequiredAttribute("AttributeSelector. Missing XPathVersion.", null);
        // TODO remove once/if xPath is supported
        throw new ParsingException("Unsupported Element: AttributeSelector");

        /*try {
            URI category = new URI(reader.getAttributeValue(null, "Category"));
            URI datatype = new URI(reader.getAttributeValue(null, "DataType"));

            String contextSelectorIdValue = reader.getAttributeValue(null, "ContextSelectorId");
            URI contextSelectorId = (contextSelectorIdValue == null) ? null : new URI(contextSelectorIdValue);

            String path = reader.getAttributeValue(null, "Path");
            if (path == null)
                throw failedToParseRequiredAttribute("Path", null);

            String mustBePresentValue = reader.getAttributeValue(null, "MustBePresent");
            if (mustBePresentValue == null || (!mustBePresentValue.equalsIgnoreCase("true") && !mustBePresentValue.equalsIgnoreCase("false")))
                throw failedToParseRequiredAttribute("MustBePresent", null);
            boolean mustBePresent = Boolean.parseBoolean(mustBePresentValue);

            reader.next();
            return new AttributeSelector(category, datatype, contextSelectorId, path, mustBePresent, defaultVersion);
        } catch (URISyntaxException e) {
            throw failedToCreate("AttributeSelector", e);
        }*/
    }

    private static VariableDefinition parseVariableDefinition(XMLStreamReader reader, Map<String,
            VariableDefinition> variableMap, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("VariableDefinition", reader);

        String variableId = reader.getAttributeValue(null, "VariableId");
        if (variableId == null)
            throw failedToParseRequiredAttribute("VariableId", null);

        reader.next();
        Expression expression = parseExpression(reader, variableMap, defaultVersion);
        if (expression == null)
            throw failedToParseRequiredAttribute("Expression", null);

        reader.next();
        return new VariableDefinition(variableId, expression);
    }

    private static Expression parseExpression(XMLStreamReader reader, Map<String, VariableDefinition> variableMap,
                                              String defaultVersion) throws ParsingException, XMLStreamException {
        if (!reader.isStartElement())
            throw cantCreateFrom("Expression", reader.getEventType());

        String name = reader.getLocalName();
        switch (name) {
            case "Apply": return parseApply(reader, variableMap, defaultVersion);
            case "AttributeValue": return parseAttributeValue(reader);
            case "AttributeDesignator": return parseAttributeDesignator(reader);
            case "AttributeSelector": return parseAttributeSelector(reader, defaultVersion);
            case "Function": return parseFunction(reader);
            case "VariableReference": return parseVariableReference(reader, variableMap);
            default: throw cantCreateFrom("Expression", name);
        }
    }

    private static Function parseFunction(XMLStreamReader reader) throws ParsingException, XMLStreamException {
        checkProperStart("Function", reader);
        try {
            Function function = FunctionFactory.getGeneralInstance().createFunction(
                    new URI(reader.getAttributeValue(null, "FunctionId")));

            reader.next();
            return function;
        } catch (FunctionTypeException | UnknownIdentifierException | URISyntaxException e) {
            throw failedToCreate("Function", e);
        }
    }

    private static VariableReference parseVariableReference(XMLStreamReader reader, Map<String, VariableDefinition> variableMap)
            throws ParsingException, XMLStreamException {
        checkProperStart("VariableReference", reader);

        String variableId = reader.getAttributeValue(null, "VariableId");
        if (variableId == null)
            throw failedToParseRequiredAttribute("VariableId", null);

        VariableDefinition variableDefinition = variableMap.get(variableId);
        if (variableDefinition == null)
            throw failedToCreate("VariableReference. VariableDefinition with given VariableId does not exist.", null);

        return new VariableReference(variableDefinition);
    }

    private static Apply parseApply(XMLStreamReader reader, Map<String, VariableDefinition> variableMap,
                                    String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("Apply", reader);

        Function function;
        try {
            function = FunctionFactory.getGeneralInstance().createFunction(
                    new URI(reader.getAttributeValue(null, "FunctionId")));
        } catch (FunctionTypeException | UnknownIdentifierException | URISyntaxException e) {
            throw failedToParseRequiredAttribute("FunctionId", e);
        }

        ParseFunction<Map<String, VariableDefinition>, BiParseFunction<XMLStreamReader, String, Expression>> f
                = vMap -> (xmlreader, xpathversion) -> parseExpression(reader, vMap, xpathversion);
        List<Expression> expressions = parseLoop(reader, defaultVersion, f.apply(variableMap));

        return new Apply(function, expressions);
    }

    private static Rule parseRule(XMLStreamReader reader, Map<String, VariableDefinition> variableMap,
                                  String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("Rule", reader);

        URI ruleId;
        try {
            ruleId = new URI(reader.getAttributeValue(null, "RuleId"));
        } catch (URISyntaxException e) {
            throw failedToParseRequiredAttribute("RuleId", e);
        }

        String ruleEffect = reader.getAttributeValue(null, "Effect");
        if (ruleEffect == null || (!ruleEffect.equalsIgnoreCase("permit") && !ruleEffect.equalsIgnoreCase("deny")))
            throw failedToParseRequiredAttribute("Effect", null);
        int effect = (ruleEffect.equalsIgnoreCase("permit")) ? Result.DECISION_PERMIT : Result.DECISION_DENY;

        String description = null;
        AbstractTarget target = null;
        Condition condition = null;
        Set<AbstractObligation> obligations = null;
        Set<AdviceExpression> advices = null;

        while (reader.hasNext()) {
            reader.next();

            if (reader.isEndElement())
                break;

            String name = reader.getLocalName();
            switch (name) {
                case "Description": description = parseDescription(reader);
                    break;
                case "Target": target = parseTarget(reader, defaultVersion);
                    break;
                case "Condition": condition = parseCondition(reader, variableMap, defaultVersion);
                    break;
                case "ObligationExpressions": obligations = parseObligationExpressions(reader, variableMap, defaultVersion);
                    break;
                case "AdviceExpressions": advices = parseAdviceExpressions(reader, variableMap, defaultVersion);
                    break;
                default: throw unexpectedElement("Rule", name);
            }
        }

        return new Rule(ruleId, effect, description, target, condition, obligations, advices, XACMLConstants.XACML_VERSION_3_0);
    }

    private static Condition parseCondition(XMLStreamReader reader, Map<String, VariableDefinition> variableMap,
                                            String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("Condition", reader);

        reader.next();
        Expression expression = parseExpression(reader, variableMap, defaultVersion);

        reader.next();
        return new Condition(expression);
    }

    private static Set<AbstractObligation> parseObligationExpressions(XMLStreamReader reader,
            Map<String, VariableDefinition> variableMap, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("ObligationExpressions", reader);

        ParseFunction<Map<String, VariableDefinition>, BiParseFunction<XMLStreamReader, String, AbstractObligation>> f
                = vMap -> (xmlreader, xpathversion) -> parseObligationExpression(reader, vMap, xpathversion);

        List<AbstractObligation> obligations = parseLoop(reader, defaultVersion, f.apply(variableMap));
        if (obligations.isEmpty())
            throw mustContain("ObligationExpressions", "ObligationExpression");

        return new LinkedHashSet<>(obligations);
    }

    private static AbstractObligation parseObligationExpression(XMLStreamReader reader,
            Map<String, VariableDefinition> variableMap, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("ObligationExpression", reader);

        URI obligationId;
        try {
            obligationId = new URI(reader.getAttributeValue(null, "ObligationId"));
        } catch (URISyntaxException e) {
            throw failedToParseRequiredAttribute("ObligationId", e);
        }

        int fulfillOn;
        String fulfillOnString = reader.getAttributeValue(null, "FulfillOn");
        if (fulfillOnString == null || (!fulfillOnString.equalsIgnoreCase("permit") && !fulfillOnString.equalsIgnoreCase("deny")))
            throw failedToParseRequiredAttribute("FulfillOn", null);
        fulfillOn = (fulfillOnString.equalsIgnoreCase("permit")) ? Result.DECISION_PERMIT : Result.DECISION_DENY;

        ParseFunction<Map<String, VariableDefinition>, BiParseFunction<XMLStreamReader, String, AttributeAssignmentExpression>> f
                = vMap -> (xmlreader, xpathversion) -> parseAttributeAssignmentExpression(reader, vMap, xpathversion);
        List<AttributeAssignmentExpression> expressions = parseLoop(reader, defaultVersion, f.apply(variableMap));

        return new ObligationExpression(fulfillOn, expressions, obligationId);
    }

    private static AttributeAssignmentExpression parseAttributeAssignmentExpression(XMLStreamReader reader,
            Map<String, VariableDefinition> variableMap, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("AttributeAssignmentExpression", reader);

        URI attributeId;
        URI category = null;
        try {
            attributeId = new URI(reader.getAttributeValue(null, "AttributeId"));
            String categoryString = reader.getAttributeValue(null, "Category");
            if (categoryString != null)
                category = new URI(categoryString);
        } catch (URISyntaxException e) {
            throw failedToCreate("AttributeAssignmentExpression", e);
        }

        String issuer = reader.getAttributeValue(null, "Issuer");

        reader.next();
        Expression expression = parseExpression(reader, variableMap, defaultVersion);

        reader.next();
        if (!reader.isEndElement())
            throw unexpectedElement("EndElement AttributeAssignmentExpression", reader.getLocalName());

        if (expression == null)
            throw failedToParseRequiredAttribute("Expression", null);

        return new AttributeAssignmentExpression(attributeId, category, expression, issuer);
    }

    private static Set<AdviceExpression> parseAdviceExpressions(XMLStreamReader reader,
            Map<String, VariableDefinition> variableMap, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("AdviceExpressions", reader);

        ParseFunction<Map<String, VariableDefinition>, BiParseFunction<XMLStreamReader, String, AdviceExpression>> f
                = vMap -> (xmlreader, xpathversion) -> parseAdviceExpression(reader, vMap, xpathversion);
        List<AdviceExpression> advices = parseLoop(reader, defaultVersion, f.apply(variableMap));

        if (advices.isEmpty())
            throw mustContain("AdviceExpressions", "AdviceExpression");

        return new LinkedHashSet<>(advices);
    }

    private static AdviceExpression parseAdviceExpression(XMLStreamReader reader,
                   Map<String, VariableDefinition> variableMap, String defaultVersion) throws ParsingException, XMLStreamException {
        checkProperStart("AdviceExpression", reader);

        URI adviceId;
        try {
            adviceId = new URI(reader.getAttributeValue(null, "AdviceId"));
        } catch (URISyntaxException e) {
            throw failedToParseRequiredAttribute("AdviceId", e);
        }

        String appliesToString = reader.getAttributeValue(null, "AppliesTo");
        if (appliesToString == null || (!appliesToString.equalsIgnoreCase("permit") && !appliesToString.equalsIgnoreCase("deny")))
            throw failedToParseRequiredAttribute("AppliesTo", null);
        int appliesTo = (appliesToString.equalsIgnoreCase("permit")) ? Result.DECISION_PERMIT : Result.DECISION_DENY;

        ParseFunction<Map<String, VariableDefinition>, BiParseFunction<XMLStreamReader, String, AttributeAssignmentExpression>> f
                = vMap -> (xmlreader, xpathversion) -> parseAttributeAssignmentExpression(reader, vMap, xpathversion);
        List<AttributeAssignmentExpression> expressions = parseLoop(reader, defaultVersion, f.apply(variableMap));

        return new AdviceExpression(adviceId, appliesTo, expressions);
    }

    //-------------------------------------

    //-------------------------------------

    private static <T> List<T> parseLoop(XMLStreamReader reader, String defaultVersion,
                                         BiParseFunction<XMLStreamReader, String, T> parseFunction) throws XMLStreamException, ParsingException {
        List<T> list = new LinkedList<>();
        while (reader.hasNext()) {
            reader.next();

            if (reader.isEndElement())
                break;

            list.add(parseFunction.apply(reader, defaultVersion));
        }
        return list;
    }

    //private static <T> List<T> parseLoop(XMLStreamReader reader, String defaultVersion, Map<String, VariableDefinition> variableMap,)

    private static boolean checkProperStart(String elementToStart, XMLStreamReader reader) throws ParsingException {
        if (!reader.isStartElement())
            throw cantCreateFrom(elementToStart, reader.getEventType());
        if (!reader.getLocalName().equals(elementToStart))
            throw cantCreateFrom(elementToStart, reader.getLocalName());

        return true;
    }

    private static boolean isBoolean(String bool) {
        return (bool != null && (bool.equalsIgnoreCase("true") || bool.equalsIgnoreCase("false")));
    }

    private static ParsingException mustContain(String element, String contain) {
        return new ParsingException(element + " must contain at least one element of " + contain);
    }

    private static ParsingException unexpectedElement(String elementToParse, String encounteredElement) {
        return new ParsingException("Encountered unexpected/unsupported element " + encounteredElement + " while parsing " + elementToParse);
    }

    private static ParsingException failedToParseRequiredAttribute(String attribute, Exception exception) {
        return new ParsingException("Failed to parse required attribute " + attribute, exception);
    }

    private static ParsingException failedToCreate(String elementName, Exception exception) {
        return new ParsingException("Failed to create element " + elementName, exception);
    }

    private static ParsingException cantCreateFrom(String elementToCreate, int eventtype) {
        return new ParsingException("Can't create " + elementToCreate + " from " + XMLEVENTS[eventtype]);
    }

    private static ParsingException cantCreateFrom(String elementToCreate, String nameFound) {
        return new ParsingException("Can't create " + elementToCreate + " from " + nameFound);
    }
}
