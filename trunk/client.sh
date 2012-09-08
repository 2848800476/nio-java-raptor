classpath=
for file in ./lib/*.jar 
do classpath=$classpath:$file
done

java -version
echo classpath:$classpath
java -Xms512m -Xmx512m -cp bin:$classpath cn.com.sparkle.raptor.test.TestClient
pause