package nl.esciencecenter.visualization.esalsa.data;

public class TimeFileAssociation {
    int          timestep;
    NetCDFReader reader;

    public TimeFileAssociation(int timestep, NetCDFReader reader) {
        this.timestep = timestep;
        this.reader = reader;
    }

    public int getTimestep() {
        return timestep;
    }

    public NetCDFReader getReader() {
        return reader;
    }
}