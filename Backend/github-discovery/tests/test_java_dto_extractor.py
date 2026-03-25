from src.java_dto_extractor import extract_schema_from_java_source


def test_java_dto_extractor_ignores_return_and_locals():
    src = """
    package com.example;

    import javax.validation.constraints.NotNull;

    public class Certif {
        @NotNull
        private Long id;

        private String name;

        public Certif getCertif() {
            String local = "x";
            return certif;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
    """

    schema = extract_schema_from_java_source(src, class_name="Certif")

    assert schema["type"] == "object"
    props = schema.get("properties") or {}

    # Real fields
    assert "id" in props
    assert "name" in props

    # Method statements/locals must not be interpreted as fields
    assert "certif" not in props
    assert "local" not in props

    assert set(schema.get("required", [])) >= {"id"}
