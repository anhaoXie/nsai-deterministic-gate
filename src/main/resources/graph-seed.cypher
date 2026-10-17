// Example policy graph for nsai-deterministic-gate graph mode.
// A fictional retail bank ("Example Bank"); not real lending policy.
// The same loan-offer rules as nsai-rules.yaml, split into shared policies that intents inherit:
//
//   LOAN_OFFER -[:INHERITS]-> CONSUMER_LENDING -[:INHERITS]-> ALL_CUSTOMER_COMMS
//
// Rule nodes use the same keys as the YAML rule file. Safe to run more than once.

CREATE CONSTRAINT intent_id IF NOT EXISTS FOR (i:Intent) REQUIRE i.id IS UNIQUE;

MERGE (i:Intent {id: 'ALL_CUSTOMER_COMMS'})
  SET i.description = 'Rules for every message sent to a customer';
MERGE (i:Intent {id: 'CONSUMER_LENDING'})
  SET i.description = 'Rules shared by all consumer lending products';
MERGE (i:Intent {id: 'LOAN_OFFER'})
  SET i.description = 'Personal-loan offers drafted by an LLM for Example Bank customers';

MATCH (child:Intent {id: 'CONSUMER_LENDING'}), (parent:Intent {id: 'ALL_CUSTOMER_COMMS'})
MERGE (child)-[:INHERITS]->(parent);
MATCH (child:Intent {id: 'LOAN_OFFER'}), (parent:Intent {id: 'CONSUMER_LENDING'})
MERGE (child)-[:INHERITS]->(parent);

// ALL_CUSTOMER_COMMS
MERGE (r:Rule {id: 'no-guaranteed-approval'})
  SET r.forbidden = '(?i)\\bguarantee(?:d)?\\s+(?:approval|approved)\\b',
      r.message = 'Offers must not promise guaranteed approval';
MATCH (i:Intent {id: 'ALL_CUSTOMER_COMMS'}), (r:Rule {id: 'no-guaranteed-approval'})
MERGE (i)-[:REQUIRES]->(r);

// CONSUMER_LENDING
MERGE (r:Rule {id: 'max-interest-rate'})
  SET r.field = 'interestRate',
      r.pattern = '(?i)\\b(?:interest rate|apr|rate)\\b\\D{0,20}?(\\d+(?:\\.\\d+)?)\\s*%',
      r.required = true,
      r.min = 3.0,
      r.max = 18.0,
      r.repair = 'clamp',
      r.message = 'Interest rate must be between 3% and 18% under the lending policy';
MERGE (r:Rule {id: 'supported-currency'})
  SET r.field = 'currency',
      r.allowed = ['USD', 'CAD'];
MATCH (i:Intent {id: 'CONSUMER_LENDING'}), (r:Rule)
WHERE r.id IN ['max-interest-rate', 'supported-currency']
MERGE (i)-[:REQUIRES]->(r);

// LOAN_OFFER
MERGE (r:Rule {id: 'client-id-required'})
  SET r.field = 'clientId',
      r.pattern = '(?i)client\\s*id\\W+([A-Z0-9-]+)',
      r.required = true,
      r.message = 'Every offer must reference a client';
MERGE (r:Rule {id: 'term-range'})
  SET r.field = 'termMonths',
      r.pattern = '(?i)(\\d+)[\\s-]*months?',
      r.min = 6,
      r.max = 84,
      r.message = 'Loan term must be 6 to 84 months';
MATCH (i:Intent {id: 'LOAN_OFFER'}), (r:Rule)
WHERE r.id IN ['client-id-required', 'term-range']
MERGE (i)-[:REQUIRES]->(r);
