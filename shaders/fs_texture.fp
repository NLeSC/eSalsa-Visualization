#version 150

in vec2 tCoord;
//in vec3 vTransformedNormal;
//in vec4 vPosition;

uniform sampler2D netcdfLatTexMap;
uniform sampler2D netcdfLonTexMap;

uniform sampler2D texture_map;

//uniform sampler2D colorTex;
//uniform sampler2D normalTex;
//uniform sampler2D specularTex;
//uniform sampler2D cityLightsTex;
            
uniform float texLonOffset;
uniform float top_texCoord;
uniform float bottom_texCoord;
uniform float opacity;

//uniform vec4 lightPos;

out vec4 fragColor;

void main() {
	float texLatScale = 1.000 / (top_texCoord - bottom_texCoord);
	float texLatStart = 0.000 - (1.000 - top_texCoord);
	vec2 transformed_tCoord = vec2(texLonOffset + tCoord.s, texLatStart + (tCoord.t*texLatScale)); 
  
  	fragColor = vec4(texture(texture_map, transformed_tCoord).rgb, opacity);
} 
