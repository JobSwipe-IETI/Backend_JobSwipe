package ieti.JobSwipe.dto;

import ieti.JobSwipe.model.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRequest {

    private String name;
    private String email;
    private String password;
    private String googleId;
    private String avatarUrl;
    private Role role;
}
