((nil . ((whitespace-style . (face
                              tabs tab-mark indentation::tab
                              spaces space-mark indentation::space
                              newline newline-mark
                              trailing))))

 (clojure-mode . ((clojure-align-forms-automatically . t)
                  (projectile-project-type . lein-test)

                  (eval . (progn
                            (define-clojure-indent
                              (compojure/defroutes 'defun)
                              (compojure/context 2)
                              (compojure/GET 2)
                              (compojure/POST 2)
                              (compojure/PUT 2)
                              (compojure/DELETE 2)
                              (compojure/HEAD 2)
                              (compojure/ANY 2)
                              (compojure/OPTIONS 2)
                              (compojure/PATCH 2)
                              (compojure/rfn 2)))))))
