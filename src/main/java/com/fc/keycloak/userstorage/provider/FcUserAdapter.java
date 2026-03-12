package com.fc.keycloak.userstorage.provider;

import com.fc.keycloak.userstorage.model.UserEntity;
import com.fc.keycloak.userstorage.repository.UserRepository;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

import java.util.HashSet;
import java.util.Set;

/**
 * Controller 같은 느낌
 */

public class FcUserAdapter extends AbstractUserAdapterFederatedStorage {

    private final UserEntity user;
    private final UserRepository userRepository;

    public FcUserAdapter(KeycloakSession session,
                         RealmModel realm,
                         ComponentModel model,
                         UserEntity user,
                         UserRepository userRepository) {
        super(session, realm, model);
        this.user = user;
        this.userRepository = userRepository;
    }

    @Override
    public String getUsername() {
        return user.getUserId();
    }

    @Override
    public void setUsername(String username) {
        user.setUserId(username);
    }

    @Override
    public String getFirstName() {
        return user.getNickname();
    }

    @Override
    public void setFirstName(String firstName) {
        user.setNickname(firstName);
        userRepository.updateNickname(user.getId(), firstName);
    }

    @Override
    public boolean isEnabled() {
        return user.getDeletedAt() == null;
    }

    @Override
    public String getId() {
        return StorageId.keycloakId(storageProviderModel, String.valueOf(user.getId()));
    }

    public UserEntity getUserEntity() {
        return user;
    }

    @Override // user 에서 role 꺼내다 씀
    protected Set<RoleModel> getRoleMappingsInternal() {
        Set<RoleModel> roles = new HashSet<>();

        String dbRole = user.getRole(); // "USER" or "ADMIN"
        if (dbRole != null && !dbRole.isBlank()) {
            RoleModel roleModel = realm.getRole(dbRole);
            if (roleModel != null) {
                roles.add(roleModel);
            }
        }
        return roles;
    }
}