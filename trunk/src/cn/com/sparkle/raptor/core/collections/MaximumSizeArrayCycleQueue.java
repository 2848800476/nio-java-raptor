package cn.com.sparkle.raptor.core.collections;

/*
 * The class is suited to limit the quene size.But the queue can't be resize when the number of elements 
 * in the queue over the maximum size.
 * Note: the queue is only suited to one thread to read ,the other thread to write.
 * 		And when write or read fail ,the code of you should retry after Thread.sleep(n).
 */
public class MaximumSizeArrayCycleQueue<T> implements Queue<T> {
	private Object[] queue;
	private int remain;
	private volatile int s;
	private volatile int e;

	public MaximumSizeArrayCycleQueue() {
		this(10);
	}

	public MaximumSizeArrayCycleQueue(int size) {
		int tsize = 2;
		while (tsize <= size)
			tsize = tsize << 2;
		queue = new Object[tsize];
		remain = tsize - 1;
		s = 0;
		e = 1;
	}

	public void push(T t) throws QueueFullException {
		int es = (e + 1) & remain;
		if (es != s) {
			queue[e] = t;
			e = es;
		} else {
			throw new QueueFullException(
					"The queue is full.You should increase the size of the queue.");
		}

	}

	public void poll() {
		int se = (s + 1) & remain;
		if (se != e) {
			s = se;
			queue[s] = null;
		}
	}

	public T peek() {
		int se = (s + 1) & remain;
		if (se != e) {
			return (T) queue[se];
		} else {
			return null;
		}
	}

	public int size() {
		return (e + remain - s) & remain;
	}
	
	public T last(){
		int es = (e + remain) & remain;
		if(es == s){
			return null;
		}else{
			return (T)queue[es];
		}
	}
	public void pollLast() {
		int es = (e + remain) & remain;
		if(es != s){
			queue[es] = null;
			e = es;
		}
	}
	public static class QueueFullException extends Exception {
		public QueueFullException(String message) {
			super(message);
		}
	}

	public static void main(String[] args) throws QueueFullException,
			InterruptedException {

		MaximumSizeArrayCycleQueue<Integer> cq = new MaximumSizeArrayCycleQueue<Integer>(
				
				10);
		for(int i = 0 ; i < 5 ; i ++){
			cq.push(1);
			cq.poll();
		}
//		cq.push(1);
//		cq.push(2);
//		cq.push(3);
		for(int i = 0 ;i < 10 ; i++){
			cq.push(i);
		}
		System.out.println(cq.last());
		cq.pollLast();
		System.out.println(cq.peek());
		cq.poll();
		
		System.out.println(cq.last());
		cq.pollLast();
		System.out.println(cq.last());
		cq.pollLast();
		
		System.out.println(cq.peek());
		cq.poll();
		/*
		for(int i = 0 ; i < 1000 ; i++){
			System.out.println("test");
			cq.push(i);
			System.out.println(cq.last());
			cq.pollLast();
			System.out.println(cq.last());
			System.out.println("size:" + cq.size());
		}*/
//		
//		System.out.println(cq.size());
//		cq.push(1);
//		System.out.println(cq.size());
//		cq.poll();
//		System.out.println(cq.size());
//		System.out.println(cq.peek());
//		System.out.println(cq.size());

		// long time = System.currentTimeMillis();
		// for (int i = 0; i < 1000000; i++) {
		// cq.push(i);
		// }
		// System.out.println(System.currentTimeMillis() - time);
		//
		// time = System.currentTimeMillis();
		// ArrayBlockingQueue<Integer> abq = new ArrayBlockingQueue<Integer>(
		// 1000000);
		// for (int i = 0; i < 1000000; i++) {
		// abq.put(i);
		// }
		// System.out.println(System.currentTimeMillis() - time);
	}

}
