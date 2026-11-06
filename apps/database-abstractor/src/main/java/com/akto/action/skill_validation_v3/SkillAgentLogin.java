package com.akto.action.skill_validation_v3;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One signed-in profile of the skill's agent; becomes the agent collection's login tags. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SkillAgentLogin {
    private String profile;
    private String email;
    private String accountType;
    private String emailCategory;
}
