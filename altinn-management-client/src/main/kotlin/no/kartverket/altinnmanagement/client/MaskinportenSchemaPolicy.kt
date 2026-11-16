package no.kartverket.altinnmanagement.client

internal const val POLICY_ACCESS_PACKAGE = "maskinporten-scopes"

internal const val POLICY_ACTION = "ScopeAccess"

internal const val POLICY_AUTHENTICATION_LEVEL = 3

/**
 * The XACML policy every MaskinportenSchema gets: anyone with the access package [POLICY_ACCESS_PACKAGE] may use the
 * resource's scopes, and delegate them, after logging in at security level [POLICY_AUTHENTICATION_LEVEL] or higher.
 * The id needs no XML escaping, since a [ResourceId] only has `a-z`, `0-9`, `_` and `-`.
 */
internal fun maskinportenSchemaPolicy(id: ResourceId): String {
    val urn = "urn:maskinportenschema:${id.value}"
    return """
        <?xml version="1.0" encoding="utf-8"?>
        <xacml:Policy xmlns:xacml="urn:oasis:names:tc:xacml:3.0:core:schema:wd-17" PolicyId="$urn:1" Version="1.0" RuleCombiningAlgId="urn:oasis:names:tc:xacml:3.0:rule-combining-algorithm:deny-overrides">
          <xacml:Target />
          <xacml:Rule RuleId="$urn:1:1" Effect="Permit">
            <xacml:Description>MaskinportenSchema resource policy for; ${id.value} for access package; $POLICY_ACCESS_PACKAGE to have access to actions; $POLICY_ACTION</xacml:Description>
            <xacml:Target>
              <xacml:AnyOf>
                <xacml:AllOf>
                  <xacml:Match MatchId="urn:oasis:names:tc:xacml:3.0:function:string-equal-ignore-case">
                    <xacml:AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">$POLICY_ACCESS_PACKAGE</xacml:AttributeValue>
                    <xacml:AttributeDesignator AttributeId="urn:altinn:accesspackage" Category="urn:oasis:names:tc:xacml:1.0:subject-category:access-subject" DataType="http://www.w3.org/2001/XMLSchema#string" MustBePresent="false" />
                  </xacml:Match>
                </xacml:AllOf>
              </xacml:AnyOf>
              <xacml:AnyOf>
                <xacml:AllOf>
                  <xacml:Match MatchId="urn:oasis:names:tc:xacml:1.0:function:string-equal">
                    <xacml:AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">${id.value}</xacml:AttributeValue>
                    <xacml:AttributeDesignator AttributeId="urn:altinn:resource" Category="urn:oasis:names:tc:xacml:3.0:attribute-category:resource" DataType="http://www.w3.org/2001/XMLSchema#string" MustBePresent="false" />
                  </xacml:Match>
                </xacml:AllOf>
              </xacml:AnyOf>
              <xacml:AnyOf>
                <xacml:AllOf>
                  <xacml:Match MatchId="urn:oasis:names:tc:xacml:3.0:function:string-equal-ignore-case">
                    <xacml:AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">$POLICY_ACTION</xacml:AttributeValue>
                    <xacml:AttributeDesignator AttributeId="urn:oasis:names:tc:xacml:1.0:action:action-id" Category="urn:oasis:names:tc:xacml:3.0:attribute-category:action" DataType="http://www.w3.org/2001/XMLSchema#string" MustBePresent="false" />
                  </xacml:Match>
                </xacml:AllOf>
              </xacml:AnyOf>
            </xacml:Target>
          </xacml:Rule>
          <xacml:ObligationExpressions>
            <xacml:ObligationExpression FulfillOn="Permit" ObligationId="$urn:obligation:1">
              <xacml:AttributeAssignmentExpression AttributeId="$urn:obligation-assignment:1" Category="urn:altinn:minimum-authenticationlevel">
                <xacml:AttributeValue DataType="http://www.w3.org/2001/XMLSchema#integer">$POLICY_AUTHENTICATION_LEVEL</xacml:AttributeValue>
              </xacml:AttributeAssignmentExpression>
            </xacml:ObligationExpression>
          </xacml:ObligationExpressions>
        </xacml:Policy>
    """.trimIndent()
}
