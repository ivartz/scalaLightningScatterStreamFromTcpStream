object Main {
    def main(args: Array[String]) {
        import java.net.ServerSocket
        import java.io.InputStream
        import java.nio.ByteBuffer
        import scala.collection.mutable.ArrayBuffer
        import org.viz.lightning._
        import scala.util.Random

        val renderQueue = scala.collection.mutable.Queue[Array[Byte]]()

        def recvAll(is: InputStream, n: Int): Array[Byte] = {
            val data = ArrayBuffer[Byte]()
                while (data.length < n) {
    	            val packet = Array.ofDim[Byte](n - data.length)
	            if (! (is.read(packet, 0, packet.length) == -1)) {
	                data ++= packet
	            }   
            }
            data.toArray
        }
        def recvMsg(is: InputStream): Array[Byte] = {
            val rawMsgLen = recvAll(is, 4)
            if (rawMsgLen.deep == Array.ofDim[Byte](4).deep){
    	        Array[Byte]()
            }
            val msgLen = ByteBuffer.wrap(rawMsgLen).getInt
            recvAll(is, msgLen)
        }
        def recvFromServerSocket(server: ServerSocket, lgn: Lightning, viz: Visualization): Unit = {
            var is = server.accept().getInputStream
            while (true) {
	        var rawMsg = recvMsg(is)
	        if (rawMsg.deep != Array[Byte]().deep) {
	           //println("pushing frame")
	            renderQueue.enqueue(rawMsg)
	        }
	        else {
	            is = server.accept().getInputStream
	        }
            }
        }
        val lgn = Lightning()
        val sessionId = "38741a99-5a22-4223-99a9-ca3793922763"
        lgn.useSession(sessionId)
        val Fs = 10000
        val startFreq = 296/8
        val endFreq = 3000/8
        val x = ( Array.fill(1 + (Fs / 2) / 8)( 296D + Random.nextDouble() * (3000D - 296D + 1D) ) )
            .slice(startFreq, endFreq+1)
        val y = (Array.fill(1 + (Fs / 2) / 8)(Random.nextDouble() * 1e11))
            .slice(startFreq, endFreq+1)
        val viz = lgn.scatterStreaming(x=x, y=y, size=3, xaxis="Hz", yaxis="pV^2 / Hz")
        val renderThread = new Thread(new Runnable {
            def run() {
                while (true) {
                    if (!renderQueue.isEmpty) {
                        val rawMsg = renderQueue.dequeue
                        val msgString = (rawMsg.map(_.toChar)).mkString
                        val points = msgString.split(";").map(_.replaceAllLiterally("(","").replaceAllLiterally(")","").split(","))
                        val x = points.map(_(0).toDouble)
                        val y = points.map(_(1).toDouble)
                        //println("plotting frame")
                        lgn.scatterStreaming(x=x, y=y, size=3, xaxis="Hz", yaxis="pV^2 / Hz", viz=viz)
                    }
                    //if (renderQueue.length < 50) {
                    //    Thread.sleep(49) // 50 - 5 ms
                    //}
                    //println(s"plotting queue size (bytes): ${renderQueue.length}")
                    //Thread.sleep(25) // 25 ms with 40 Hz arrival
                    Thread.sleep(50) // 2 * 25 ms since 40/2 Hz arrival
                }
            }
        })
        val server = new ServerSocket(54322)
        println("now listening on port 54322")
        renderThread.start()
        recvFromServerSocket(server, lgn, viz)
    }   
}
