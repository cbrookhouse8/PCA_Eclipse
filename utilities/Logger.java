package utilities;

public class Logger {

	private String callingClass;
	
	public Logger(Object clazz) {
		callingClass = clazz.getClass().getSimpleName();
	}

	public void info(String msg) {
		System.out.println(this.callingClass + ": " + msg);
	}
	
}
