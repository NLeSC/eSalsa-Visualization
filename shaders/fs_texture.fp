#version 150

in vec2 tCoord;
in vec3 vTransformedNormal;
in vec4 vPosition;

uniform sampler2D netcdfLatTexMap;
uniform sampler2D netcdfLonTexMap;

uniform sampler2D texture_map;

uniform sampler2D colorTex;
uniform sampler2D normalTex;
uniform sampler2D specularTex;
uniform sampler2D cityLightsTex;
            
uniform float texLonOffset;
uniform float top_texCoord;
uniform float bottom_texCoord;
uniform float opacity;
uniform float intensity;
uniform vec4 lightPos;

out vec4 fragColor;

//void main() {
	//float texLatScale = 1.000 / (top_texCoord - bottom_texCoord);
	//float texLatStart = 0.000 - (1.000 - top_texCoord);
	//vec2 transformed_tCoord = vec2(texLonOffset + tCoord.s, texLatStart + (tCoord.t*texLatScale)); 
  
  	//fragColor = vec4(texture(texture_map, transformed_tCoord).rgb, opacity);
//} 

void main(void) {
	float texLatScale = 1.000 / (top_texCoord - bottom_texCoord);
	float texLatStart = 0.000 - (1.000 - top_texCoord);
	vec2 transformed_tCoord = vec2(texLonOffset + tCoord.s, texLatStart + (tCoord.t*texLatScale)); 
	vec4 colorMapColor = vec4(texture(texture_map, transformed_tCoord).rgb, opacity);
	
	float texLatScale_earth = 1.018;
	float texLatStart_earth = -0.018;
	//texLatScale_earth = 1.0 / (1 - texLatStart_earth);
	
	vec2 transformed_tCoord_earth = vec2(tCoord.s, texLatStart_earth + (tCoord.t*texLatScale_earth));
	
	vec3 AmbientColor = vec3(0.15,0.15,0.15);
	vec3 PointLightingLocation = lightPos.xyz ; //vec3(-30.0,4.0,-20.0);
	vec3 PointLightingSpecularColor = vec3(5.0,5.0,5.0);
	vec3 PointLightingDiffuseColor = vec3(0.8,0.8,0.8);

    vec3 lightWeighting;
    
    vec3 lightDirection = normalize(PointLightingLocation - vPosition.xyz);
    vec3 bump = normalize( texture2D(normalTex, transformed_tCoord_earth).xyz * 2.0 - 1.0);
    vec3 normal = normalize(vTransformedNormal);

    float specularLightWeighting = 0.0;
    float shininess = 32.0;

    shininess = 255.0 -(texture2D(specularTex, transformed_tCoord_earth).r *220.0) ;

	float diffuseLightWeighting = max(dot(normal, lightDirection), 0.0);

    if (shininess < 220.0) {
        vec3 eyeDirection = normalize(-vPosition.xyz);
        vec3 reflectionDirection = reflect(-lightDirection, normal);

		if (diffuseLightWeighting > 0) {        
        	specularLightWeighting = pow(max(dot(reflectionDirection, eyeDirection), 0.0), shininess);
        }
    }    
    
    lightWeighting = AmbientColor
        + PointLightingSpecularColor * specularLightWeighting
        + PointLightingDiffuseColor * diffuseLightWeighting;

    vec4 earthColor;
    earthColor = texture2D(colorTex, transformed_tCoord_earth);
    
    vec4 finalColor = vec4(earthColor.rgb * lightWeighting, earthColor.a);
        
    vec4 cityLightsColor = texture2D(cityLightsTex, transformed_tCoord_earth);
    
    if (length(lightWeighting) < 0.7) {
    	finalColor = finalColor + (0.7 - length(lightWeighting)) *cityLightsColor;
    }
    
    fragColor = mix(finalColor, colorMapColor, intensity);
}