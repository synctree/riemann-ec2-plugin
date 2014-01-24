# riemann-ec2-plugin

A Riemann plugin providing various utility functions in regard to EC2.

## Synopsis

```clojure

(load-plugins)

(ec2/start-ec2 {:interval 5 :credentials {:endpoint "us-west-2"}})

(streams
  (fn [event] (prn "host data" (ec2/get-host-info (:host event))))
  (ec2/running-stream prn index))
```

## License

Copyright © 2014 Campanja AB.

Distributed under the Apache License, Version 2.0.
