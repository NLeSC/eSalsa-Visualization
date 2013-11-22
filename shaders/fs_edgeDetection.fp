#version 150

in vec2 tCoord;
uniform int width;
uniform int height;

uniform sampler2D texture_map;

out vec4 fragColor;

float threshold(in float thr1, in float thr2 , in float val) {
	if (val < thr1) {
		return 0.0;
	}
	
	if (val > thr2) {
		return 1.0;
	}
	
	return val;
}

// averaged pixel intensity from 3 color channels
float avg_intensity(in vec4 pix) {
 	return (pix.r + pix.g + pix.b) / 3.0;
}

vec4 get_pixel(in vec2 coords, in float dx, in float dy) {
 	return texture2D(texture_map, coords + vec2(dx, dy));
}

// returns pixel color
float IsEdge(in vec2 coords){
  	float dxtex = 1.0 / float(width) /*image width*/;
  	float dytex = 1.0 / float(height) /*image height*/;
  	float pix[9];
  	int k = -1;
  	float delta;

  	// read neighboring pixel intensities
  	for (int i=-1; i<2; i++) {
   		for(int j=-1; j<2; j++) {
    		k++;
    		pix[k] = avg_intensity(get_pixel(coords,float(i)*dxtex,
                                          float(j)*dytex));
   		}
  	}

  	// average color differences around neighboring pixels
  	delta = (abs(pix[1]-pix[7]) + abs(pix[5]-pix[3]) + abs(pix[0]-pix[8]) + abs(pix[2]-pix[6]))/4.0;

  	return threshold(0.25, 0.4, clamp(1.8 * delta, 0.0, 1.0));
}

/*
vec3 normal(vec2 tcCenter) {  	
  	vec2 tcEast;
  	vec2 tcWest;
  	vec2 tcNorth;
  	vec2 tcSouth;
  	
  	if (tCoord.x >= 0) {
  		tcEast = vec2(tcCenter.x-1, tcCenter.y);
  	} else {
  		tcEast = vec2(tcCenter.x, tcCenter.y);
  	} 
  	
  	if (tCoord.x < width) {
  		tcWest = vec2(tcCenter.x+1, tcCenter.y);
	} else {
  		tcWest = vec2(tcCenter.x, tcCenter.y);
  	} 
  	 
  	if (tCoord.y >= 0) {
  	 	tcNorth = vec2(tcCenter.x, tcCenter.y-1);
  	} else {
  		tcNorth = vec2(tcCenter.x, tcCenter.y);
  	}   	
  	
  	if (tCoord.y < height) {
  		tcSouth = vec2(tcCenter.x, tcCenter.y+1);
	} else {
  		tcSouth = vec2(tcCenter.x, tcCenter.y);
  	} 
  	
  	float edgeEast = IsEdge(tcEast);
  	float edgeWest = IsEdge(tcWest);
  	float edgeNorth = IsEdge(tcNorth);
  	float edgeSouth = IsEdge(tcSouth);  	
}
*/

void main() {
  	//vec4 color = vec4(0.0, 0.0, 0.0, 1.0);
  	//vec2 tcCenter = tCoord;
  	
  	//color.g = IsEdge(tCoord);
    fragColor = vec4(texture(texture_map, tCoord).rgb, 1.0);
    //fragColor = color;
}