package com.archana.jobs.util;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class SkillExtractor {

    // More specific entries first to avoid partial matches
    private static final List<String> KNOWN_SKILLS = List.of(
            // Spring ecosystem
            "Spring Boot", "Spring MVC", "Spring Cloud", "Spring Security", "Spring Data",
            "Spring Batch", "Spring Integration", "Spring WebFlux", "Spring",
            // Core Java
            "Java", "Java 8", "Java 11", "Java 17", "Java 21",
            "Multithreading", "Concurrency", "JVM", "JDK", "JUnit", "Mockito",
            "Lombok", "MapStruct", "Reactor",
            // Architecture
            "Microservices", "Monolith", "Event-Driven", "CQRS", "Saga",
            "Domain-Driven Design", "DDD", "Design Patterns", "SOLID",
            "REST", "RESTful", "SOAP", "gRPC", "GraphQL", "WebSocket",
            // Messaging
            "Kafka", "RabbitMQ", "ActiveMQ", "SQS", "SNS", "Pub/Sub",
            // Databases
            "PostgreSQL", "MySQL", "Oracle", "MSSQL", "SQL Server",
            "MongoDB", "Cassandra", "DynamoDB", "Couchbase",
            "Redis", "Memcached",
            "Elasticsearch", "Solr", "OpenSearch",
            "Hibernate", "JPA", "JDBC", "MyBatis", "Flyway", "Liquibase",
            // Cloud & DevOps
            "AWS", "GCP", "Azure", "EC2", "S3", "Lambda", "EKS", "ECS",
            "Docker", "Kubernetes", "K8s", "Helm", "Istio",
            "Terraform", "Ansible", "Pulumi",
            "Jenkins", "GitHub Actions", "GitLab CI", "CircleCI", "ArgoCD",
            "CI/CD", "Git", "GitLab", "Bitbucket",
            "Linux", "Unix", "Shell",
            // Observability
            "Prometheus", "Grafana", "Datadog", "Splunk", "ELK", "Kibana", "Logstash",
            "Jaeger", "Zipkin", "OpenTelemetry",
            // Security
            "OAuth", "OAuth2", "JWT", "SSO", "SAML", "Keycloak", "LDAP",
            // API & Docs
            "Swagger", "OpenAPI", "Postman",
            // Languages
            "Kotlin", "Python", "Go", "Golang", "Scala",
            // Frontend (for fullstack roles)
            "React", "Angular", "Vue", "TypeScript", "JavaScript",
            // Fintech specific
            "Payments", "Payment Gateway", "Fintech", "UPI", "SWIFT", "ISO 20022",
            "SEPA", "ACH", "NEFT", "RTGS", "PCI DSS", "PCI", "KYC", "AML",
            "FIX Protocol", "Lending", "Credit", "Fraud Detection",
            // Process
            "Agile", "Scrum", "Kanban", "TDD", "BDD", "SDLC",
            // Build tools
            "Maven", "Gradle",
            // Other
            "Apache Camel", "Quarkus", "Micronaut"
    );

    public static String extract(String... texts) {
        String combined = String.join(" ", texts).toLowerCase();
        Set<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (String skill : KNOWN_SKILLS) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(skill) + "\\b",
                    Pattern.CASE_INSENSITIVE);
            if (p.matcher(combined).find()) {
                found.add(skill);
            }
        }

        return found.isEmpty() ? null : String.join(",", found);
    }
}
