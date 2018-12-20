((nil . ((projectile-project-type . lein-test)
         (clojure-align-forms-automatically . t)
         (eval . (progn
                   ;; Define custom indentation for functions inside metabase.
                   ;; This list isn't complete; add more forms as we come across them.
                   (define-clojure-indent
                     (defroutes 'defun)
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