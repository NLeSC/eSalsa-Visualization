#version 150

in vec2 tCoord;

uniform sampler2D texture_map;
uniform float top_texCoord;
uniform float bottom_texCoord;
uniform float opacity;

out vec4 fragColor;

void main() {
	float texScale = 1.000 / (top_texCoord - bottom_texCoord);
	float texStart = 0.000 - (1.000 - top_texCoord);
	vec2 transformed_tCoord = vec2(tCoord.s, texStart + (tCoord.t*texScale)); 
    fragColor = vec4(texture(texture_map, transformed_tCoord).rgb, opacity);
} 
