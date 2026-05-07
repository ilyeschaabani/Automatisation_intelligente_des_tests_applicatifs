package com.pfe.platform.msexecution.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "environments")
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String baseUrlWeb;
    private String baseUrlApi;
    @Column(columnDefinition = "JSONB")
    private String variables;

    public Environment() {
    }

    public Environment(Long id, String baseUrlWeb, String baseUrlApi, String variables) {
        this.id = id;
        this.baseUrlWeb = baseUrlWeb;
        this.baseUrlApi = baseUrlApi;
        this.variables = variables;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBaseUrlWeb() {
        return baseUrlWeb;
    }

    public void setBaseUrlWeb(String baseUrlWeb) {
        this.baseUrlWeb = baseUrlWeb;
    }

    public String getBaseUrlApi() {
        return baseUrlApi;
    }

    public void setBaseUrlApi(String baseUrlApi) {
        this.baseUrlApi = baseUrlApi;
    }

    public String getVariables() {
        return variables;
    }

    public void setVariables(String variables) {
        this.variables = variables;
    }
}
