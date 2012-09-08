package cn.com.sparkle.raptor.core.collections;

public class LastAccessTimeLinkedList<T> {
	private Entity<T> head = new Entity<T>(null);
	public LastAccessTimeLinkedList() {
		head.next = head;
		head.prev = head;
	}
	public void putOrMoveFirst(Entity<T> e){
		//remove entity from link
		remove(e);
		//insert to head
		e.next = head.next;
		e.prev = head;
		e.next.prev = e;
		e.prev.next = e;
		
	}
	public Entity<T> getLast(){
		if(head.prev == head){
			return null;
		}
		return head.prev;
	}
	public void remove(Entity<T> e){
		e.prev.next = e.next;
		e.next.prev = e.prev;
		e.next = e;
		e.prev = e;
	}
	public static class Entity<T>{
		private Entity<T> prev = this;
		private Entity<T> next = this;
		private T element;
		public Entity(T t){
			this.element = t;
		}
		public T getElement(){
			return element;
		}
	}
	public static void main(String[] args) {
		LastAccessTimeLinkedList<Object> l = new LastAccessTimeLinkedList<Object>();
		Entity<Object> e1 = new Entity<Object>(1);
		Entity<Object> e2 = new Entity<Object>(2);
		Entity<Object> e3 = new Entity<Object>(3);
		Entity<Object> e4 = new Entity<Object>(4);
		Entity<Object> e5 = new Entity<Object>(5);
		l.putOrMoveFirst(e1);
		l.putOrMoveFirst(e2);
		l.putOrMoveFirst(e3);
		l.putOrMoveFirst(e4);
		l.putOrMoveFirst(e5);
		l.print();
		
		l.putOrMoveFirst(e1);
		l.print();
		l.putOrMoveFirst(e4);
		l.print();
		
		
		l.remove(e3);
		l.remove(e5);
		l.remove(e1);
		l.remove(e2);
		l.remove(e4);
		l.putOrMoveFirst(e1);
		l.remove(e1);
		l.print();
		
	}
	private void print(){
		
		Entity<T> t = head;
		while((t = t.next) != head){
			System.out.println(t.element);
		}
		System.out.println("--------------------------" + getLast().element);
		
	}
}
