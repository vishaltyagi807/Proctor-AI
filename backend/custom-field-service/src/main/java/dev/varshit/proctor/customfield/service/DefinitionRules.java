package dev.varshit.proctor.customfield.service;

import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.enums.CustomFieldType;
import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.customfield.dto.CreateCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.UpdateCustomFieldRequest;
import dev.varshit.proctor.customfields.CustomFieldValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class DefinitionRules {

    private final CustomFieldValidator validator;

    public DefinitionRules(CustomFieldValidator validator) {
        this.validator = validator;
    }

    public void validateCreate(CreateCustomFieldRequest r) {
        check(r.entity(), r.dataType(), r.options(), r.defaultValue(), r.minValue(), r.maxValue(), r.maxLength(),
                r.pattern(), r.appliesToRoleId(), r.appliesToDepartmentId(), r.key(), Boolean.TRUE.equals(r.required()));
    }

    public void validateUpdate(CustomFieldDefinitionDTO existing, UpdateCustomFieldRequest r) {
        check(existing.entity(), existing.dataType(), r.options(), r.defaultValue(), r.minValue(), r.maxValue(),
                r.maxLength(), r.pattern(), r.appliesToRoleId(), r.appliesToDepartmentId(), existing.key(),
                Boolean.TRUE.equals(r.required()));
    }

    private void check(CustomFieldEntity entity, CustomFieldType type, List<String> options, Object defaultValue,
                       BigDecimal min, BigDecimal max, Integer maxLength, String pattern, UUID roleId,
                       UUID departmentId, String key, boolean required) {
        boolean choice = type == CustomFieldType.select || type == CustomFieldType.multi_select;
        if (choice && (options == null || options.isEmpty())) {
            throw new BadRequestException("Select fields need at least one option");
        }
        if (!choice && options != null && !options.isEmpty()) {
            throw new BadRequestException("Options are only allowed for select fields");
        }
        if ((min != null || max != null) && type != CustomFieldType.number) {
            throw new BadRequestException("min and max are only allowed for number fields");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BadRequestException("min must not be greater than max");
        }
        if ((maxLength != null || (pattern != null && !pattern.isBlank())) && type != CustomFieldType.text) {
            throw new BadRequestException("maxLength and pattern are only allowed for text fields");
        }
        if (pattern != null && !pattern.isBlank()) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw new BadRequestException("pattern is not a valid regular expression");
            }
        }
        if (entity == CustomFieldEntity.departments && roleId != null) {
            throw new BadRequestException("Department fields cannot be restricted to a role");
        }
        if (entity == CustomFieldEntity.roles && departmentId != null) {
            throw new BadRequestException("Role fields cannot be restricted to a department");
        }
        if (defaultValue != null) {
            CustomFieldDefinitionDTO probe = new CustomFieldDefinitionDTO(null, entity, key, key, null, type, required,
                    options, null, min, max, maxLength, pattern, roleId, departmentId, 0, true, null, null);
            validator.normalize(probe, defaultValue);
        }
    }
}
