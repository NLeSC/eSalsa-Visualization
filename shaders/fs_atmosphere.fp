#version 150

in vec2 vTextureCoord;
in vec3 vTransformedNormal;
in vec4 vPosition;

uniform vec4 lightPos;

out vec4 fragColor;

void main() {
	vec3 AmbientColor = vec3(0.25,0.25,0.25);  //vec3(0.3, 0.6, 1.0);
	vec3 PointLightingLocation = lightPos.xyz; //vec3(-30.0,4.0,-20.0);
	vec3 PointLightingDiffuseColor = vec3(0.8,0.8,0.8);
	
	vec3 lightWeighting;
    
    vec3 lightDirection = normalize(PointLightingLocation - vPosition.xyz);
    vec3 normal = normalize(vTransformedNormal);

    float diffuseLightWeighting = max(dot(normal, lightDirection), 0.0);
    lightWeighting = AmbientColor + PointLightingDiffuseColor * diffuseLightWeighting;
    
    float cloudAlpha = 0.5; //1.0-texture2D(cloudTransparencyTex, vTextureCoord).r;    
    vec4 cloudColor = vec4(0.3, 0.6, 1.0, 1.0); //texture2D(cloudTex, vTextureCoord);
    
    vec4 atmoColor = vec4((cloudColor * vec4(lightWeighting, 1.0)).rgb,  cloudAlpha);
    
    fragColor = atmoColor;	
	
	
	//vec3 matColor = vec3(0.3, 0.6, 1.0);	
	//vec3 eye_direction = vec3(0.0, 0.0, 1.0);	
	//float dotP = dot(vertex_normal, eye_direction);
	//fragColor = vec4(matColor,0.5-(dotP/2.0));
} 