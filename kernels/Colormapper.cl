
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
    
    int dataIndex = iy * sizeX + ix;
    float inputValue = data[dataIndex];
    
    int outputRow = sizeY - iy;
    int outputColumn = sizeX -ix;
        
    int outputIndex = (outputRow*sizeX)+outputColumn;
    
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
	    float alpha = (inputValue - minValue)/diff;
	    int colorIndex = (int)(alpha * colorMapSize);
	    output[outputIndex] = colorMap[colorIndex];
    }
}