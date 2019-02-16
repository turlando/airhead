(ns airhead-frontend.components
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [clojure.string :refer [blank? split lower-case]]
            [reagent.core :as r]
            [airhead-frontend.state :refer [app-state update-state!]]
            [airhead-frontend.requests :as req]
            [markdown.core :refer [md->html]]))

(defn icon [i]
  [:img.icon {:src (str "icons/" (name i) ".svg")}])

;; -------------------------
;; Header

(defn header []
  (let [cursor  (r/cursor app-state [:info])
        title   (@cursor :name)
        message (@cursor :greet_message)]
    [:header
     [:h1 title]
     [:div {:dangerouslySetInnerHTML
            {:__html (-> message md->html str)}}]]))

;; -------------------------
;; Player

(defn playbar-button [title action icon]
  [:button.pure-button.pure-u-1-4 {:title title :on-click action} icon])

(defn pause-button [audio]
  [playbar-button "Pause" #(.pause audio) (icon :pause)])

(defn play-button [audio]
  [playbar-button "Play" #(.play audio) (icon :play)])

(defn skip-button []
  [playbar-button "Skip" req/playlist-skip! (icon :step-forward)])

(defn audio-on-button [audio]
  [playbar-button "Mute" #(set! (.-muted audio) true) (icon :volume-up)])

(defn audio-off-button [audio]
  [playbar-button "Unmute" #(set! (.-muted audio) false) (icon :volume-off)])

(defn open-stream-link [url]
  [:a.pure-button.pure-u-1-4 {:title "Open stream" :href url :target "_blank"}
   (icon :external-link)])

(defn right-box [head body]
  [:div.right-box head [:span body]])

(defn now-playing []
  (let [track (@app-state :now-playing)]
    [right-box (icon :music)
     (if track
       (str (:artist track) " - " (:title track))
       [:em "Nothing is playing."])]))

(defn player-section []
  (let [audio-ref (r/atom nil)]
    (fn []
      (when-let [url (get-in @app-state [:info :stream_url])]
        [:section#player
         [:audio.hidden {:ref #(reset! audio-ref %)}
          [:source {:src url
                    :type "audio/ogg"}]]

         (when-let [audio @audio-ref]
           [:div.controller-box
            [:div.pure-button-group
             {:role "group"}

             (if (.-paused audio)
               [play-button audio]
               [pause-button audio])

             [skip-button]

             (if (.-muted audio)
               [audio-off-button audio]
               [audio-on-button audio])

             [open-stream-link url]]

            [now-playing]])]))))

;; -------------------------
;; Tracks

(defn playlist-add-button [track]
  [:button.track-action
   {:title "Add to playlist"
    :on-click #(req/playlist-add! (:uuid track))}
   (icon :plus)])

(defn playlist-remove-button [track]
  [:button.pure-button.track-action
   {:title "Remove from playlist"
    :on-click #(req/playlist-remove! (:uuid track))}
   (icon :minus)])

(defn track-field [text]
  [:td {:title text} text])

(defn track-tr [track action-button]
  [:tr.track
   [:td
    (when action-button
      [action-button track])]
   [track-field (track :title)]
   [track-field (track :artist)]
   [track-field (track :album)]])

;; -------------------------
;; Upload

(defn info-uploading [file-name loaded total]
  [:div.upload-info
   [:div [:progress.pure-input-1 {:max total :value loaded}]]
   [:div (str "Uploading: " file-name)]])

(defn info-transcoding [file-name]
  [:div.upload-info
   [:div [:progress.pure-input-1]]
   [:div (str "Transcoding: " file-name)]])

(defn info-done [track]
  [:div.upload-info
   [:div "Done!"]
   [right-box
    [playlist-add-button track]
    (str (:artist track) " - " (:title track))]])

(defn info-error [status error-msg]
  [:div.upload-info
   [:div (str "Error " status)]
   [right-box [:div (str error-msg)]]])

(defn upload-info [{loaded :loaded
                    total  :total
                    status :status
                    file-name :file-name
                    {error-msg :msg
                     track-id  :track} :body}]
  (let [library (@app-state :library)
        done? (fn [] (some #(= track-id %)
                           (map :uuid library)))]
    (cond
      (= status 200)
      (if (done?)
        [info-done (->> library
                        (filter #(= (:uuid %) track-id))
                        first)]
        [info-transcoding file-name])

      (and status (not= status 200))
      [info-error status error-msg]

      :else
      [info-uploading file-name loaded total])))

(defn file-select-button [file-input-ref]
  [:div.pure-button.pure-u-1-2
   {:title "Select a file"
    :on-click #(when @file-input-ref
                 (.click @file-input-ref))}
   (icon :folder-open)])

(defn get-file-name [file-input-ref]
  (let [path (.-value @file-input-ref)]
    (if-not (blank? path)
      (-> path (split "\\") last)  ; C:\fakepath\file-name
      nil)))

(defn upload-button [form-ref file-input-ref]
  [:div.pure-button.pure-button.pure-u-1-2
   {:title "Upload"
    :on-click #(when-let   [form      @form-ref]
                 (when-let [file-name (get-file-name file-input-ref)]

                   (let [up-chan      (req/upload! form)
                         upload-state (r/cursor app-state [:uploads])]

                     (set! (.-value @file-input-ref) "")
                     (go-loop []
                              (when-let [delta (<! up-chan)]
                                (swap! upload-state merge
                                       {(hash up-chan)
                                        (merge delta {:file-name file-name})})
                                (recur))))))}
   (icon :upload)])

(defn upload-section []
  (let [form-ref       (r/atom nil)
        file-input-ref (r/atom nil)]
    (fn []
      [:section#upload
       [:h2 "Upload"]

       [:form.hidden {:ref #(reset! form-ref %)}

        [:input {:type "file" :name "track"
                 :ref #(reset! file-input-ref %)}]]

       [:div.controller-box
        [:div.pure-button-group
         [file-select-button file-input-ref]
         [upload-button form-ref file-input-ref]]

        [right-box (icon :file-o)
         (when @file-input-ref
           (or (get-file-name file-input-ref)
               "No file selected."))]]

       (for [[k upload] (@app-state :uploads)]
        ^{:key k} [upload-info upload])])))

;; -------------------------
;; Tables

(defn table [head content]
  [:table.pure-table.pure-table-horizontal
   [:thead head]
   [:tbody content]])

;; -------------------------
;; Playlist

(defn playlist-section []
  [:section#playlist
   [:h2 "Playlist"]
   (if-let [tracks (not-empty (@app-state :playlist))]
     [table
      [:tr [:th] [:th  "Title"] [:th "Artist"] [:th "Album"]]
      (for [track tracks]
        ^{:key track} [track-tr track playlist-remove-button])]
     "The playlist is empty.")])

;; -------------------------
;; Search

(defn on-query-change [e]
  (update-state! :query (-> e .-target .-value))
  (req/get-library!))

;; -------------------------
;; Library

(defn update-sort-field! [new-field]
  (if (= new-field (:sort-field @app-state))
    (swap! app-state update-in [:ascending] not)
    (swap! app-state assoc :ascending true))
  (swap! app-state assoc :sort-field new-field))

(defn sort-tracks [tracks]
  (let [sorted-tracks (sort-by (:sort-field @app-state) tracks)]
    (if (:ascending @app-state)
      sorted-tracks
      (rseq sorted-tracks))))

(defn sorting-th [field caption]
  [:th.sorting-th {:title (str "Sort by " (lower-case caption))
                   :on-click #(update-sort-field! field)}
   [:span caption]
   [:span.sorting-th-arrow (when (= field (@app-state :sort-field))
                          (if (@app-state :ascending)
                            "▲"
                            "▼"))]])

(defn library-section []
  (let [tracks (@app-state :library)]
    [:section#library
     [:h2 "Library"]
     [:form#library-search.pure-form
      [:input {:type "text"
               :id "query"
               :placeholder "Search through the library..."
               :value (@app-state :query)
               :on-change on-query-change}]]
     [table
      [:tr [:th]
       [sorting-th :title "Title"]
       [sorting-th :artist "Artist"]
       [sorting-th :album "Album"]]
      (for [track (sort-tracks tracks)]
        ^{:key track} [track-tr track playlist-add-button])]]))

;; -------------------------
;; Main

(defn page-component []
  [:main
   [header]
   [player-section]
   [upload-section]
   [playlist-section]
   [library-section]])
