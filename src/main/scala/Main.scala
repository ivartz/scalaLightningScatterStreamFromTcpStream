object Main {
    def main(args: Array[String]) {
        import java.net.ServerSocket
        import java.io.InputStream
        import java.nio.ByteBuffer
        import scala.collection.mutable.ArrayBuffer
        import org.viz.lightning._
        import scala.util.Random
        // The session ID for the Lightning-viz plotting server.
        // needs to be updated to the current created session in
        // http://localhost:3000/sessions/ .
        val sessionId = "f2ad0fb8-52b6-4bbb-8603-af8bd3059b18"
        // The queue/buffer used for storing raw Messages 
        // (Each message contains a PSD after is is converted to string)
        val renderQueue = scala.collection.mutable.Queue[Array[Byte]]()
        // The following three functions are a custom 
        // TCP protocol for receiving variable sized
        // Array[Byte] messages.
        def recvAll(is: InputStream, n: Int): Array[Byte] = {
            // Receive an Array[Byte] message of length n bytes
            // length of the Array[Byte].
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
            // Receive an entire message, 
            // this is a PSD after converted to string.
            // Always first receive the first 4 bytes,
            // which should the an int, the length
            // of the preceiding message (the Array[Bytes] of a PSD).
            val rawMsgLen = recvAll(is, 4)
            if (rawMsgLen.deep == Array.ofDim[Byte](4).deep){
    	        Array[Byte]()
            }
            val msgLen = ByteBuffer.wrap(rawMsgLen).getInt
            recvAll(is, msgLen)
        }
        def recvFromServerSocket(server: ServerSocket, lgn: Lightning, viz: Visualization): Unit = {
            // The continously running receiver.
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
        // Connect to the lightning visualization server.
        val lgn = Lightning()
        lgn.useSession(sessionId)
        // Parameters for decoding message.
        val Fs = 10000
        val startFreq = 296/8
        val endFreq = 3000/8
        // Parameters for plotting axes.
        val x = ( Array.fill(1 + (Fs / 2) / 8)( 296D + Random.nextDouble() * (3000D - 296D + 1D) ) )
            .slice(startFreq, endFreq+1)
        /*
        val y = (Array.fill(1 + (Fs / 2) / 8)(Random.nextDouble() * 1e11))
            .slice(startFreq, endFreq+1) // This is best for visualizing the raw unfiltered PSDs (.flatMap{s: Option[Seq[Seq[Double]]] => s.get} in spark streaming).
        */
        val y = (Array.fill(1 + (Fs / 2) / 8)(Random.nextDouble() * 1e10))
            .slice(startFreq, endFreq+1) // This worked well with -48 db and less (<48) attenuation in spark streaming (in lowPassFilterBinsWithTresholds).
        /*
        val y = (Array.fill(1 + (Fs / 2) / 8)(Random.nextDouble() * 1D))
            .slice(startFreq, endFreq+1) // This is for visualizing the intermediate lowPassFilteredAttenuationGains in lowPassFilterBinsWithTresholds .
        */
        // Create a scatterStreaming plot.
        val viz = lgn.scatterStreaming(x=x, y=y, size=3, xaxis="Hz", yaxis="pV^2 / Hz")
        // Start the plotting (render) thread.
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
                        //println(msgString)
                        lgn.scatterStreaming(x=x, y=y, size=3, xaxis="Hz", yaxis="pV^2 / Hz", viz=viz)
                    }
                    //if (renderQueue.length < 50) {
                    //    Thread.sleep(49) // 50 - 5 ms
                    //}
                    println(s"queue size (PSDs): ${renderQueue.length}")
                    //Thread.sleep(25) // 25 ms with 40 Hz arrival
                    Thread.sleep(50) // 2 * 25 ms since 40/2 Hz arrival
                }
            }
        })
        // Create a ServerSocket in order to listen to Spark.
        val server = new ServerSocket(54322)
        println("now listening on port 54322")
        renderThread.start()
        recvFromServerSocket(server, lgn, viz)
    }   
}
