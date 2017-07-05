
#define MAX(x, y) (x > y ? x : y)
#define MIN(x, y) (x < y ? x : y)

__kernel void mapColors(
	__global float *data,
    __global uint *output,
    __global uint *colorMap,
    unsigned int sizeX, 
    unsigned int sizeY,
    float fillValue,
    float minValue, 
    float maxValue,
    unsigned int fillColor,
    unsigned int colorMapSize
    )
{
    unsigned int ix = get_global_id(0);
    unsigned int iy = get_global_id(1);

	if (ix >= sizeX) { return; }
	if (iy >= sizeY) { return; }
    
    int dataIndex = iy * sizeX + ix;
    float inputValue = data[dataIndex];
    
    int outputRow = sizeY - iy;
    int outputColumn = sizeX -ix;
        
    int outputIndex = (outputRow*sizeX)+outputColumn;
	if (outputIndex > 0 && outputIndex < (sizeX*sizeY-1)) {        
	    if (inputValue == fillValue) {
	    	output[outputIndex] = fillColor;
	    } else if (inputValue < minValue){
		    int colorIndex = 0;
		    output[outputIndex] = colorMap[colorIndex];
	    } else if (inputValue > maxValue){
		    int colorIndex = colorMapSize-1;
		    output[outputIndex] = colorMap[colorIndex];
	    } else {
	    	float diff = maxValue - minValue;
	    	
	    	if (diff > 0.0) {            	
		    	float newMinValue = 0.0;
		    	float newMaxValue = log(1.0 + diff);
		    	float newInputValue = log(1.0 + inputValue);
			    	    
			    float alpha = newInputValue/newMaxValue;
			    int colorIndex = (int)(alpha * colorMapSize);
		    	colorIndex = MIN(MAX(0, colorIndex), colorMapSize-1);
			    output[outputIndex] = colorMap[colorIndex];
		    } else {
		    	output[outputIndex] = 0.0;
		    }
	    }
    }
}