export default {
  BOLA: "Attacker can access resources of any user by changing the auth token in request.",
  ADD_USER_ID: "Attacker can access resources of any user by adding user_id in URL.",
  PRIVILEGE_ESCALATION: "Attacker can access resources of any user by replacing method of the endpoint (eg: change"
   +"method from get to post). This way attacker can get access to unauthorized endpoints.",
   NO_AUTH: "API doesn’t validate the authenticity of token. Attacker can remove the auth token and access the endpoint."
}