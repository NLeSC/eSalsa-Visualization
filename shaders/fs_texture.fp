#version 150

in vec2 tCoord;

uniform sampler2D texture_map;
uniform float texLonOffset;
uniform float top_texCoord;
uniform float bottom_texCoord;
uniform float opacity;

out vec4 fragColor;

void main() {
	float texLatScale = 1.000 / (top_texCoord - bottom_texCoord);
	float texLatStart = 0.000 - (1.000 - top_texCoord);
	vec2 transformed_tCoord = vec2(texLonOffset + tCoord.s, texLatStart + (tCoord.t*texLatScale)); 
    fragColor = vec4(texture(texture_map, transformed_tCoord).rgb, opacity);
} 
