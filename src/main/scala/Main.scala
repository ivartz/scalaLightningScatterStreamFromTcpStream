object Main {
    def main(args: Array[String]) {
        import java.net.ServerSocket
        import java.io.InputStream
        import java.nio.ByteBuffer
        import scala.collection.mutable.ArrayBuffer
        import org.viz.lightning._
        import scala.util.Random

        def recvAll(is: InputStream, n: Int): Array[Byte] = {
            val data = ArrayBuffer[Byte]()
            while (data.length < n) {
                val packet = Array.ofDim[Byte](n - data.length)
                if (is.read(packet, 0, packet.length) == -1) {
                    Array[Byte]()
                }
                data ++= packet   
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
        def recvAndPlotFromServerSocket(server: ServerSocket, lgn: Lightning, viz: Visualization): Unit = {
            var is = server.accept().getInputStream
            while (true) {
                var rawMsg = recvMsg(is)
                if (rawMsg.deep != Array[Byte]().deep) {
                    val msgString = (rawMsg.map(_.toChar)).mkString
                    val points = msgString.split(";").map(_.replaceAllLiterally("(","").replaceAllLiterally(")","").split(","))
                    val x = points.map(_(0).toDouble)
                    val y = points.map(_(1).toDouble)
                    lgn.scatterStreaming(x=x, y=y, size=3, xaxis="Hz", yaxis="pV^2 / Hz", viz=viz)
                }
                else {
                    is = server.accept().getInputStream
                }
            }
        }
        val lgn = Lightning()
        val sessionId = "77307eab-4e9e-4016-850d-97a1d1f4a12b"
        lgn.useSession(sessionId)
        val Fs = 10000
        val startFreq = 296/8
        val endFreq = 3000/8
        val x = ( Array.fill(1 + (Fs / 2) / 8)
                 ( 296D + 
                  Random.nextDouble() * 
                  (3000D - 296D + 1D) ) )
         .slice(startFreq, endFreq+1)
        val y = (Array.fill(1 + (Fs / 2) / 8)(Random.nextDouble() * 1e11)).slice(startFreq, endFreq+1)
        val viz = lgn.scatterStreaming(x=x, y=y, size=3, xaxis="Hz", yaxis="pV^2 / Hz")
        val server = new ServerSocket(54322)
        println("now listening on port 54322")
        recvAndPlotFromServerSocket(server, lgn, viz)
    }
}
