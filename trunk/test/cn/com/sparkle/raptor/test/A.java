package cn.com.sparkle.raptor.test;

public class A {
	public static boolean a =false;
	private static class  B extends Thread{
		public void run(){
			while(true){
				if(a){
					
					System.out.println(a);
					a = false;
				}
				else
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	private static class  C extends Thread{
		public void run(){
			while(true){
				if(!a){
					System.out.println(a);
					a = true;
				}
				else
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	public static volatile long curTime;
	public static void main(String[] args) {
//		new B().start();
//		new C().start();
		
		
		new D().start();
		long time = System.currentTimeMillis();
		long tt;
		for(int i = 0 ; i < 10000000;i++){
			tt = curTime;
		}
		System.out.println(System.currentTimeMillis() - time);
	}
	static class  D extends Thread{
		public void run(){
			while(true){
				curTime = System.currentTimeMillis();
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
