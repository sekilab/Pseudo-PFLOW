package particle;

import java.util.List;

public interface IParticle {
	public void updateWeight();
	
	public double getWeight();
	
	public void predict();
	
	public IParticle resample(IParticle particle);
	
	public List<Double> getParameters();
}
