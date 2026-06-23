package com.pfe.platform.msexecution.dto;

import java.util.List;

/**
 * Schéma d'un formulaire extrait du DOM (oracle déterministe pour le test fonctionnel).
 * Contient les contraintes HTML5 réelles déclarées sur chaque champ — c'est la
 * "vérité terrain" qui permet de dériver les cas de test et de juger les résultats.
 */
public class FormSchema {

    private int index;
    private String id;
    private String name;
    private String action;
    private String method;
    private boolean hasSubmit;
    private List<FormField> fields;

    public FormSchema() {}

    /** Signature stable d'un formulaire (pour ne pas le tester deux fois). */
    public String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(action == null ? "" : action).append("|");
        if (fields != null) {
            for (FormField f : fields) {
                sb.append(f.getName()).append(",");
            }
        }
        return sb.toString();
    }

    public String label() {
        if (name != null && !name.isBlank()) return name;
        if (id != null && !id.isBlank()) return id;
        if (action != null && !action.isBlank()) return action;
        return "formulaire #" + index;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public boolean isHasSubmit() { return hasSubmit; }
    public void setHasSubmit(boolean hasSubmit) { this.hasSubmit = hasSubmit; }
    public List<FormField> getFields() { return fields; }
    public void setFields(List<FormField> fields) { this.fields = fields; }

    /** Un champ de formulaire avec ses contraintes HTML5. */
    public static class FormField {
        private String name;
        private String label;
        private String tag;      // input | textarea | select
        private String type;     // text | email | password | number | tel | url | date | checkbox ...
        private boolean required;
        private String pattern;
        private Integer minLength;
        private Integer maxLength;
        private String min;
        private String max;
        private List<String> options;

        public FormField() {}

        /** A-t-il une contrainte de format vérifiable (hors required) ? */
        public boolean hasFormatConstraint() {
            if (type == null) return false;
            String t = type.toLowerCase();
            return t.equals("email") || t.equals("url") || t.equals("tel") || t.equals("number")
                    || (pattern != null && !pattern.isBlank());
        }

        public boolean hasBoundary() {
            return (minLength != null && minLength > 0)
                    || (maxLength != null && maxLength > 0)
                    || (min != null && !min.isBlank())
                    || (max != null && !max.isBlank());
        }

        public String displayName() {
            if (label != null && !label.isBlank()) return label;
            if (name != null && !name.isBlank()) return name;
            return type != null ? "champ " + type : "champ";
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getTag() { return tag; }
        public void setTag(String tag) { this.tag = tag; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public Integer getMinLength() { return minLength; }
        public void setMinLength(Integer minLength) { this.minLength = minLength; }
        public Integer getMaxLength() { return maxLength; }
        public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }
        public String getMin() { return min; }
        public void setMin(String min) { this.min = min; }
        public String getMax() { return max; }
        public void setMax(String max) { this.max = max; }
        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }
    }
}
