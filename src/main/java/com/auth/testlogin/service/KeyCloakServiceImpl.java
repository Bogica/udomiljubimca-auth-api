package com.auth.testlogin.service;

import com.auth.testlogin.model.UserCredentials;
import com.auth.testlogin.model.dto.TokenDto;
import com.auth.testlogin.model.dto.UserInfoDto;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.servlet.ServletRequest;
import java.util.LinkedHashMap;

/**
 * @author Djordje
 * @version 1.0
 */
@Component
public class KeyCloakServiceImpl implements KeyCloakService {

    @Value("${keycloak.credentials.secret}")
    private String SECRETKEY;

    @Value("${keycloak.resource}")
    private String CLIENTID;

    @Value("${keycloak.auth-server-url}")
    private String AUTHURL;

    @Value("${keycloak.realm}")
    private String REALM;

    @Autowired
    private RestTemplate restTemplate;

    // Get Token
    public TokenDto getToken(UserCredentials userCredentials, ServletRequest request) throws Exception {

        TokenDto tokenDto;
        try {
            MultiValueMap<String, String> mapForm = new LinkedMultiValueMap<>();
            mapForm.add("grant_type", "password");
            mapForm.add("client_id", CLIENTID);
            mapForm.add("username", userCredentials.getUsername());
            mapForm.add("password", userCredentials.getPassword());
            mapForm.add("client_secret", SECRETKEY);

            //get token
            tokenDto = exchange(mapForm);

            //get user info by access token
            if (tokenDto != null) {
                tokenDto.setUserInfo(getUserInfo(tokenDto.getAccessToken()));
            }
            return tokenDto;

        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    public UserInfoDto getUserInfo(String token) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);

        HttpEntity<String> entity = new HttpEntity<>(token, headers);

        UserInfoDto userInfoDto = restTemplate.exchange(
                AUTHURL + "/realms/" + REALM + "/protocol/openid-connect/userinfo",
                HttpMethod.GET, entity, UserInfoDto.class).getBody();

        return userInfoDto;
    }

    public TokenDto getByRefreshToken(String refreshToken) {
        TokenDto tokenDto = null;
        try {
            MultiValueMap<String, String> mapForm = new LinkedMultiValueMap<>();
            mapForm.add("client_id", CLIENTID);
            mapForm.add("grant_type", "refresh_token");
            mapForm.add("refresh_token", refreshToken.substring(7));
            mapForm.add("client_secret", SECRETKEY);

            tokenDto = exchange(mapForm);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return tokenDto;
    }

    // after logout user from the keycloak system. No new access token will be issued
    public void logoutUser(String userId) {

        UsersResource userRessource = getKeycloakUserResource();
        userRessource.get(userId).logout();

    }

    // Reset passowrd
    public void resetPassword(CredentialRepresentation cr, String token, String userId) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CredentialRepresentation> entity = new HttpEntity<>(cr, headers);
        cr.setType(CredentialRepresentation.PASSWORD);

        String fedUserId = getUserInfo(token).getSub();

        restTemplate.put(AUTHURL + "/admin/realms/" + REALM + "/users/" + fedUserId + "/reset-password",
                entity
        );

    }

    private UsersResource getKeycloakUserResource() {

        Keycloak kc = KeycloakBuilder.builder().serverUrl(AUTHURL).realm(REALM).username("admin").password("admin")
                .clientId("admin-cli").resteasyClient(new ResteasyClientBuilder().connectionPoolSize(10).build())
                .build();

        RealmResource realmResource = kc.realm(REALM);
        UsersResource userRessource = realmResource.users();

        return userRessource;
    }

    // New method for exchange using Rest Template
    private TokenDto exchange(MultiValueMap<String, String> mapForm) {

        TokenDto tokenDto = new TokenDto();

        String uri = AUTHURL + "/realms/" + REALM + "/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.valueOf(String.valueOf(MediaType.APPLICATION_FORM_URLENCODED)));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(mapForm, headers);
        // TODO: 18.4.21. Response model mapping
        ResponseEntity<Object> response = restTemplate.exchange(uri, HttpMethod.POST, request, Object.class);
        LinkedHashMap<String, Object> map = (LinkedHashMap<String, Object>) response.getBody();

        if (map != null) {
            tokenDto.setAccessToken(map.get("access_token").toString());
            tokenDto.setTokenType(map.get("token_type").toString());
            tokenDto.setRefreshToken(map.get("refresh_token").toString());
            tokenDto.setExpires_in(map.get("expires_in").toString());
            tokenDto.setScope(map.get("scope").toString());
        } else {
            return null;
        }
        return tokenDto;

    }

}