# Volume-based Covert Channel

Simple implementation of a volume-base covert channel for two Android apps: one sender and one receiver. 
The secret to be transported is the IMEI number being read by the sender and could be sent out to the internet 
by the receiver. The goal is to bypass Android's permission mechanism that protects user privacy
as well as tools that statically analyze apps' source code to detect information leakage.

## The Apps

One application, called ImeiSender is granted access to the phone state by using the permission 
*android.permission.READ_PHONE_STATE*. This allows it to obtain the IMEI number of the device which should be considered as 
confidential because this number can be used to identify the device. The other application, called ImeiReceiver 
does not have any permission to read sensitive data. The implementation of these applications is pretty simple:
* *ImeiSender*: this application uses Handle to repeat runnable tasks and add delay between the runs.
There are three Runnable tasks corresponding to three phases of the sender: the Startup-, Waiting- and Sending phases.
* *ImeiReceiver*: this application also uses Handle and runs three Runnable
tasks corresponding to three phases of the receiver: the Waiting-, Preparing-
and Receiving phases.

## The Covert Channel
The covert channel used by the provided applications is based on system volume, in particular the one identified by 
*AudioManager.STREAM_MUSIC* flag. The source modifies the system volume such that he set it to maximal value in
order to transmit bit *1* and to minimal value to transmit bit *0*, meanwhile the sink repeatedly observes the system
volume to extract the confidential data. It is crucial to point out that this kind of covert channel requires synchronization
to achieve accuracy. Hence, a synchronization protocol between the two application is proposed in which the
two parties exchange a sync time point with help of a single legal intent-based communication.

The whole communication is divided into 4 phases:

* *Phase 1* - Gathering Confidential Data: The sender reads IMEI number of the device and compute its binary representation.
* *Phase 2* - Making Agreement on Sync Time Point: The sender determine a reasonable sync time point and inform the receiver
via a legal intent-based communication. The time point used in the provided applications is 5ms after the sender has 
successfully collected the confidential data.
* *Phase 3* - Synchronization: Shortly (in comparison to the delay between two iterations in the sending phase, specified by
the constant *EARLY_INFORM* in *ImeiSender*) before the sync time point , the sender inform the receiver that transmission 
is about to performed via the covert channel. In this case, *ImeiSender* set volume to max value (the receiver had set volume
to minnimal value before) in order to notify *ImeiReceiver*.
* *Phase 4* - Transmission of Data: Confidential data is now transmitted bit-wise via the covert channel. The frequency of 
the receiving process must be much higher than that of the sending process in order to achieve accuracy. Moreover, the duration
of the receiving process must also be longer. Hence, it is crucial that the both parties agree on a fix bit-length of the data
such that the observed result will be truncated accordingly.
